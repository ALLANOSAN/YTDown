# Remover Chaquopy e Embutir CPython 3.14.7 Oficial — Plano de Implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use `superpowers:subagent-driven-development` (recomendado) ou
> `superpowers:executing-plans` para executar tarefa a tarefa. Os passos usam checkbox (`- [ ]`).

**Goal:** Trocar o Chaquopy pelo pacote oficial CPython 3.14.7 para Android, mantendo intactos o download
via yt-dlp, a atualização do yt-dlp em runtime sem reempacotar APK, e a gravação de capa/artista/álbum/título
nos arquivos.

**Architecture:** O código Python **não muda**. Ele já expõe funções que retornam string JSON. Substituímos
só a ponte: no lugar do `com.chaquo.python.Python`, uma lib JNI própria (`libpybridge.so`) que inicializa o
`libpython3.14.so` oficial via `Py_InitializeFromConfig` e expõe uma única função de despacho
`call(module, func, argsJson) -> String`. Um `PyRuntime` em Kotlin substitui o `PythonBridge`, e uma flag em
DataStore permite alternar entre as duas implementações até a validação terminar.

**Tech Stack:** CPython 3.14.7 (Android embeddable package oficial), NDK 29.0.14206865, CMake 4.1.2,
AGP 9.2.1, Kotlin 2.3.21, JNI/C, yt-dlp + mutagen + requests + beautifulsoup4 (puro Python, vendorizados).

## Global Constraints

- Python alvo: **3.14.7**, pacote oficial `python-3.14.7-<arch>-linux-android.tar.gz` de
  <https://www.python.org/downloads/android/>.
- ABIs: **arm64-v8a** e **x86_64** — são os únicos que a python.org publica pré-compilados. `armeabi-v7a` só
  existe se você mesmo compilar (`android.py configure-host arm-linux-androideabi`); fora do escopo deste plano.
- `minSdk` do build oficial é **API 24**; o projeto usa 26. Compatível, não mexer.
- O contrato JSON das funções Python é **imutável** neste plano. Nenhum arquivo em
  `android/app/src/main/python/` pode mudar de assinatura ou de formato de retorno.
- O Chaquopy só é removido na **Tarefa 9**, depois de tudo verde. Até lá as duas pontes coexistem.
- Toda tarefa termina com commit. Nenhuma tarefa deixa o build vermelho.
- Testes instrumentados exigem aparelho/emulador conectado via `adb`.

## Expectativa honesta de ganho

Medido no APK atual (`unzip -l`):

| | Tamanho |
|---|---|
| Chaquopy hoje (2 ABIs) | 21,4 MB nativo + ~20 MB assets = **~41,6 MB** |
| CPython próprio, estimado | ~11,8 MB nativo × 2 ABIs + stdlib podada ~21 MB (comprime para ~8-12 MB) + site-packages ~6 MB = **~40 MB** |

**Este plano não encolhe o APK de forma relevante.** O que ele entrega é controle: qualquer versão/patch do
Python quando você quiser, sem depender do calendário do Chaquopy nem do teto de AGP dele (hoje 9.2).
Se o objetivo for tamanho, o alvo certo é `libffmpeg_exe.so` (46,7 MB — o maior item isolado do APK).

**Custo permanente:** você passa a manter a ponte JNI que o Chaquopy dava pronta.

---

## Estrutura de Arquivos

**Criar:**
- `scripts/fetch_python_android.sh` — baixa e poda o prefix oficial para as duas ABIs
- `android/app/src/main/cpp/CMakeLists.txt` — compila a ponte JNI
- `android/app/src/main/cpp/py_bridge.c` — init do interpretador, redirect de stdio, despacho
- `android/app/src/main/python/_bridge.py` — despachante Python (import + getattr + chamada)
- `android/app/src/main/kotlin/com/example/ytdown/core/python/PyRuntime.kt` — fachada Kotlin
- `android/app/src/main/kotlin/com/example/ytdown/core/python/PythonAssets.kt` — extrai stdlib/site-packages para `filesDir`
- `android/app/src/androidTest/java/com/example/ytdown/core/python/PyRuntimeTest.kt` — testes instrumentados
- `android/app/src/main/python/tests/test_bridge.py` — teste do despachante (roda no host)

**Modificar:**
- `android/app/build.gradle` — sourceSets do cpp, externalNativeBuild, tarefa de empacotamento
- `android/app/src/main/kotlin/com/example/ytdown/core/business/YtDlpWrapper.kt` — trocar Chaquopy por `PyRuntime`
- `android/app/src/main/kotlin/com/example/ytdown/core/artwork/PythonMetadataBridge.kt` — idem
- `android/app/src/main/kotlin/com/example/ytdown/MainActivity.kt:52-75` — remover o diagnóstico Chaquopy
- `android/app/src/main/kotlin/com/example/ytdown/PythonBridge.kt` — deletado na Tarefa 9

**Não tocar:** `download.py`, `fetch.py`, `metadata.py`, `runtime.py`, `helpers.py`, `enrich.py`,
`metal_archives.py`, `metadata_pipeline.py`, `logger.py`, `ytdown.py`.

---

## Tarefa 1: Golden master do contrato Python

Sem isto não há como provar que a ponte nova devolve o mesmo que a antiga.

**Files:**
- Create: `android/app/src/main/python/tests/test_contract.py`
- Create: `android/app/src/main/python/tests/fixtures/` (gerado pelo teste)

**Interfaces:**
- Produces: fixtures JSON em `tests/fixtures/*.json`, consumidas pela Tarefa 7.

- [ ] **Passo 1: Escrever o teste que falha**

```python
# android/app/src/main/python/tests/test_contract.py
"""Congela o formato de retorno das funcoes publicas. Se uma chave sumir, quebra aqui."""
import json
import os
import unittest

import ytdown

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures")

CHAVES_OBRIGATORIAS = {
    "fetch_video_info": {"success", "title", "is_playlist"},
    "download_video": {"success", "filename", "tags_injected"},
    "rewrite_file_metadata": {"success"},
    "check_yt_dlp_update": {"current_version", "latest_version", "update_available"},
}


class TestContratoPublico(unittest.TestCase):
    def test_todas_funcoes_publicas_estao_exportadas(self):
        esperado = {
            "download_video", "fetch_video_info", "rewrite_file_metadata",
            "check_yt_dlp_update", "update_yt_dlp_if_needed", "search_metadata",
            "get_band_details", "get_similar_bands", "get_band_albums",
        }
        self.assertEqual(esperado, set(ytdown.__all__))

    def test_toda_funcao_publica_retorna_string_json(self):
        for nome in ytdown.__all__:
            self.assertTrue(callable(getattr(ytdown, nome)), f"{nome} nao e chamavel")

    def test_fixture_de_falha_tem_as_chaves_do_contrato(self):
        from helpers import _failure_payload
        payload = json.loads(_failure_payload("erro X", stage="extract_info", retryable=True))
        self.assertEqual(False, payload["success"])
        self.assertEqual("extract_info", payload["stage"])
        self.assertTrue(payload["retryable"])
        os.makedirs(FIXTURES, exist_ok=True)
        with open(os.path.join(FIXTURES, "failure_payload.json"), "w") as f:
            json.dump(payload, f, indent=2, sort_keys=True)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
cd android/app/src/main/python
PYTHONPATH=. python3 tests/test_contract.py
```

Esperado: FALHA. `_failure_payload` tem assinatura própria — confirme os nomes reais dos parâmetros em
`helpers.py:17` e ajuste a chamada até o teste refletir o comportamento real. O teste só vale depois de você
ter visto ele falhar por motivo legítimo.

- [ ] **Passo 3: Corrigir o teste até passar contra o código atual**

Nenhum código de produção muda. O teste descreve o que já existe.

- [ ] **Passo 4: Rodar e ver passar**

```bash
cd android/app/src/main/python
PYTHONPATH=. python3 tests/test_contract.py
```

Esperado: `OK`.

- [ ] **Passo 5: Commit**

```bash
git add android/app/src/main/python/tests/test_contract.py android/app/src/main/python/tests/fixtures
git commit -m "test: congelar contrato JSON das funcoes publicas do python"
```

---

## Tarefa 2: Baixar e podar o CPython oficial

**Files:**
- Create: `scripts/fetch_python_android.sh`

**Interfaces:**
- Produces: `android/app/src/main/cpython/<abi>/prefix/` com `lib/libpython3.14.so`, `lib/lib*_python.so`,
  `lib/python3.14/` podado. Consumido pelas Tarefas 3 e 4.

- [ ] **Passo 1: Escrever o teste que falha**

```bash
# scripts/test_fetch_python.sh
#!/bin/bash
# Verifica que o prefix foi baixado, podado e tem os arquivos que o Gradle espera.
set -e
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FALHAS=0

for abi in arm64-v8a x86_64; do
  P="$ROOT/android/app/src/main/cpython/$abi/prefix"
  for f in lib/libpython3.14.so lib/libssl_python.so lib/libcrypto_python.so lib/python3.14/os.py; do
    [ -f "$P/$f" ] || { echo "FALTA: $abi/$f"; FALHAS=1; }
  done
  [ -d "$P/lib/python3.14/test" ] && { echo "PODA FALHOU: $abi ainda tem test/"; FALHAS=1; }
  [ -d "$P/lib/python3.14/idlelib" ] && { echo "PODA FALHOU: $abi ainda tem idlelib/"; FALHAS=1; }
  tam=$(du -sm "$P/lib/python3.14" | cut -f1)
  [ "$tam" -gt 25 ] && { echo "GORDO: $abi stdlib $tam MB (esperado <= 25)"; FALHAS=1; }
done

[ "$FALHAS" -eq 0 ] && echo "OK" || exit 1
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
chmod +x scripts/test_fetch_python.sh && ./scripts/test_fetch_python.sh
```

Esperado: `FALTA: arm64-v8a/lib/libpython3.14.so` — o diretório nem existe ainda.

- [ ] **Passo 3: Escrever o script**

```bash
# scripts/fetch_python_android.sh
#!/bin/bash
# Baixa o Android embeddable package oficial do CPython e poda o que nao vai pro APK.
set -euo pipefail

PY_VERSION="${PY_VERSION:-3.14.7}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/android/app/src/main/cpython"

# host CPython -> ABI Android
declare -A ALVOS=(
  [aarch64-linux-android]=arm64-v8a
  [x86_64-linux-android]=x86_64
)

# Pesa 37 MB de test/ + 1,9 MB de idlelib. Nada disso roda no app.
PODAR=(test idlelib tkinter turtledemo lib2to3 ensurepip pydoc_data __phello__)

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

for host in "${!ALVOS[@]}"; do
  abi="${ALVOS[$host]}"
  tarball="python-${PY_VERSION}-${host}.tar.gz"
  url="https://www.python.org/ftp/python/${PY_VERSION}/${tarball}"

  echo "==> baixando $tarball"
  curl -fL --retry 3 -o "$TMP/$tarball" "$url"

  echo "==> extraindo para $abi"
  rm -rf "$DEST/$abi"
  mkdir -p "$DEST/$abi"
  tar xzf "$TMP/$tarball" -C "$DEST/$abi" ./prefix

  STDLIB="$DEST/$abi/prefix/lib/python3.14"
  for d in "${PODAR[@]}"; do
    rm -rf "${STDLIB:?}/$d"
  done
  find "$STDLIB" -name "__pycache__" -type d -prune -exec rm -rf {} +

  echo "==> $abi pronto: $(du -sh "$STDLIB" | cut -f1) de stdlib"
done

echo "OK"
```

- [ ] **Passo 4: Rodar o script e depois o teste**

```bash
chmod +x scripts/fetch_python_android.sh
./scripts/fetch_python_android.sh
./scripts/test_fetch_python.sh
```

Esperado: `OK`. Se `GORDO` aparecer, acrescente diretórios em `PODAR`.

- [ ] **Passo 5: Ignorar o prefix no git e commitar só os scripts**

O prefix pesa ~50 MB por ABI. Não versione — o script reconstrói.

```bash
echo "android/app/src/main/cpython/" >> .gitignore
git add scripts/fetch_python_android.sh scripts/test_fetch_python.sh .gitignore
git commit -m "build: script para baixar e podar o CPython 3.14.7 oficial de Android"
```

---

## Tarefa 3: Empacotar o CPython no APK

**Files:**
- Modify: `android/app/build.gradle`
- Create: `android/app/src/androidTest/java/com/example/ytdown/core/python/PythonPackagingTest.kt`

**Interfaces:**
- Consumes: `android/app/src/main/cpython/<abi>/prefix/` da Tarefa 2.
- Produces: no APK, `lib/<abi>/libpython3.14.so` e o asset `python/stdlib.zip`.

- [ ] **Passo 1: Escrever o teste instrumentado que falha**

```kotlin
// android/app/src/androidTest/java/com/example/ytdown/core/python/PythonPackagingTest.kt
package com.example.ytdown.core.python

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PythonPackagingTest {

    @Test
    fun libpython_esta_no_diretorio_de_libs_nativas() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val lib = File(ctx.applicationInfo.nativeLibraryDir, "libpython3.14.so")
        assertTrue("libpython3.14.so ausente em ${lib.parent}", lib.exists())
    }

    @Test
    fun stdlib_zip_esta_nos_assets() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val nomes = ctx.assets.list("python")?.toList() ?: emptyList()
        assertTrue("assets/python contem $nomes", nomes.contains("stdlib.zip"))
    }
}
```

- [ ] **Passo 2: Habilitar androidTest e rodar para ver falhar**

Adicione em `android/app/build.gradle`, dentro de `defaultConfig`:

```groovy
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
```

E nas dependências:

```groovy
    androidTestImplementation "androidx.test.ext:junit:1.2.1"
    androidTestImplementation "androidx.test:runner:1.6.2"
```

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*PythonPackagingTest*'
```

Esperado: FALHA nos dois testes — nada foi empacotado ainda.

- [ ] **Passo 3: Empacotar via Gradle**

Adicione em `android/app/build.gradle`, no nível superior (fora de `android { }`):

```groovy
// Copia as libs nativas do CPython oficial para jniLibs e zipa a stdlib como asset.
def cpythonRoot = file("src/main/cpython")
def pyGenDir = layout.buildDirectory.dir("generated/cpython")

tasks.register("packagePythonLibs", Copy) {
    from(cpythonRoot) {
        include "*/prefix/lib/libpython*.so"
        include "*/prefix/lib/lib*_python.so"
        eachFile { it.path = "${it.path.split('/')[0]}/${it.name}" }
    }
    into pyGenDir.map { it.dir("jniLibs") }
    includeEmptyDirs = false
}

tasks.register("packagePythonStdlib", Zip) {
    // A stdlib e identica entre ABIs (so os .so de lib-dynload diferem, e esses
    // vao junto porque o loader do Python os procura dentro do proprio zip path).
    from("$cpythonRoot/arm64-v8a/prefix/lib/python3.14")
    archiveFileName = "stdlib.zip"
    destinationDirectory = pyGenDir.map { it.dir("assets/python") }
    // Sem compressao extra: o APK ja comprime, e descomprimir 2x custa boot.
    entryCompression = ZipEntryCompression.DEFLATED
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
        .configureEach { dependsOn("packagePythonLibs") }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
        .configureEach { dependsOn("packagePythonStdlib") }
```

E dentro de `android { sourceSets { ... } }`:

```groovy
        main.jniLibs.srcDirs += files("$buildDir/generated/cpython/jniLibs")
        main.assets.srcDirs += files("$buildDir/generated/cpython/assets")
```

- [ ] **Passo 4: Rodar e ver passar**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*PythonPackagingTest*'
```

Esperado: PASS nos dois. Se `libpython3.14.so` não aparecer, confira se
`packaging { jniLibs { useLegacyPackaging = true } }` continua ativo — sem isso o Android não extrai o `.so`
para `nativeLibraryDir`.

- [ ] **Passo 5: Commit**

```bash
git add android/app/build.gradle android/app/src/androidTest
git commit -m "build: empacotar libpython3.14 e stdlib.zip no APK"
```

---

## Tarefa 4: Ponte JNI — inicializar o interpretador

**Files:**
- Create: `android/app/src/main/cpp/CMakeLists.txt`
- Create: `android/app/src/main/cpp/py_bridge.c`
- Create: `android/app/src/main/kotlin/com/example/ytdown/core/python/PyRuntime.kt`
- Create: `android/app/src/main/kotlin/com/example/ytdown/core/python/PythonAssets.kt`
- Modify: `android/app/src/androidTest/java/com/example/ytdown/core/python/PyRuntimeTest.kt`

**Interfaces:**
- Produces: `PyRuntime.start(context)`, `PyRuntime.version(): String`. Consumido pelas Tarefas 5 a 8.

- [ ] **Passo 1: Escrever o teste que falha**

```kotlin
// android/app/src/androidTest/java/com/example/ytdown/core/python/PyRuntimeTest.kt
package com.example.ytdown.core.python

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class PyRuntimeTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun iniciar() {
            PyRuntime.start(InstrumentationRegistry.getInstrumentation().targetContext)
        }
    }

    @Test
    fun interpretador_reporta_a_versao_3_14_7() {
        val v = PyRuntime.version()
        assertTrue("sys.version foi '$v'", v.startsWith("3.14.7"))
    }
}
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*PyRuntimeTest*'
```

Esperado: FALHA de compilação — `PyRuntime` não existe.

- [ ] **Passo 3: Escrever a ponte**

```c
// android/app/src/main/cpp/py_bridge.c
#include <jni.h>
#include <Python.h>
#include <android/log.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>

#define TAG "PyBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Le de um pipe e joga no logcat. stdout/stderr do Python somem sem isto.
typedef struct { int fd; int prio; const char *tag; } stream_t;

static void *bombear(void *arg) {
    stream_t *s = (stream_t *) arg;
    char buf[4000];
    ssize_t n;
    while ((n = read(s->fd, buf, sizeof(buf) - 1)) > 0) {
        buf[n] = '\0';
        __android_log_write(s->prio, s->tag, buf);
    }
    return NULL;
}

static void redirecionar(int fd_origem, int prio, const char *tag) {
    int p[2];
    if (pipe(p) != 0) return;
    dup2(p[1], fd_origem);
    close(p[1]);
    stream_t *s = malloc(sizeof(stream_t));
    s->fd = p[0]; s->prio = prio; s->tag = tag;
    pthread_t t;
    pthread_create(&t, NULL, bombear, s);
    pthread_detach(t);
}

JNIEXPORT jint JNICALL
Java_com_example_ytdown_core_python_PyRuntime_nativeStart(
        JNIEnv *env, jobject thiz, jstring j_home, jstring j_path) {

    if (Py_IsInitialized()) return 0;

    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    redirecionar(STDOUT_FILENO, ANDROID_LOG_INFO,  "python.stdout");
    redirecionar(STDERR_FILENO, ANDROID_LOG_ERROR, "python.stderr");

    const char *home = (*env)->GetStringUTFChars(env, j_home, NULL);
    const char *path = (*env)->GetStringUTFChars(env, j_path, NULL);

    PyConfig config;
    PyConfig_InitIsolatedConfig(&config);
    config.write_bytecode = 0;   // filesDir e gravavel, mas .pyc so incha
    config.module_search_paths_set = 1;

    PyStatus status = PyConfig_SetBytesString(&config, &config.home, home);
    if (PyStatus_Exception(status)) goto erro;

    // path vem como lista separada por ':' vinda do Kotlin
    char *copia = strdup(path);
    for (char *tok = strtok(copia, ":"); tok; tok = strtok(NULL, ":")) {
        wchar_t *w = Py_DecodeLocale(tok, NULL);
        PyWideStringList_Append(&config.module_search_paths, w);
        PyMem_RawFree(w);
    }
    free(copia);

    status = Py_InitializeFromConfig(&config);
    if (PyStatus_Exception(status)) goto erro;

    PyConfig_Clear(&config);
    (*env)->ReleaseStringUTFChars(env, j_home, home);
    (*env)->ReleaseStringUTFChars(env, j_path, path);
    LOGI("interpretador iniciado");
    return 0;

erro:
    LOGE("falha ao iniciar: %s", status.err_msg ? status.err_msg : "desconhecido");
    PyConfig_Clear(&config);
    (*env)->ReleaseStringUTFChars(env, j_home, home);
    (*env)->ReleaseStringUTFChars(env, j_path, path);
    return -1;
}

JNIEXPORT jstring JNICALL
Java_com_example_ytdown_core_python_PyRuntime_nativeVersion(JNIEnv *env, jobject thiz) {
    PyGILState_STATE gil = PyGILState_Ensure();
    PyObject *sys = PyImport_ImportModule("sys");
    PyObject *ver = PyObject_GetAttrString(sys, "version");
    const char *s = PyUnicode_AsUTF8(ver);
    jstring out = (*env)->NewStringUTF(env, s ? s : "");
    Py_XDECREF(ver);
    Py_XDECREF(sys);
    PyGILState_Release(gil);
    return out;
}
```

```cmake
# android/app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22)
project(pybridge C)

set(PREFIX "${CMAKE_CURRENT_SOURCE_DIR}/../cpython/${ANDROID_ABI}/prefix")

add_library(pybridge SHARED py_bridge.c)

target_include_directories(pybridge PRIVATE "${PREFIX}/include/python3.14")

add_library(python3.14 SHARED IMPORTED)
set_target_properties(python3.14 PROPERTIES
        IMPORTED_LOCATION "${PREFIX}/lib/libpython3.14.so")

target_link_libraries(pybridge python3.14 log)
```

```kotlin
// android/app/src/main/kotlin/com/example/ytdown/core/python/PythonAssets.kt
package com.example.ytdown.core.python

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

/** Extrai a stdlib dos assets para filesDir uma vez por versao. */
object PythonAssets {

    private const val VERSAO = "3.14.7"

    fun ensureInstalled(context: Context): File {
        val destino = File(context.filesDir, "python/$VERSAO")
        val marcador = File(destino, ".ok")
        if (marcador.exists()) return destino

        destino.deleteRecursively()
        destino.mkdirs()

        context.assets.open("python/stdlib.zip").use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entrada = zip.nextEntry
                while (entrada != null) {
                    val alvo = File(destino, entrada.name)
                    // Zip Slip: recusa entrada que escapa do destino.
                    require(alvo.canonicalPath.startsWith(destino.canonicalPath)) {
                        "entrada de zip fora do destino: ${entrada.name}"
                    }
                    if (entrada.isDirectory) {
                        alvo.mkdirs()
                    } else {
                        alvo.parentFile?.mkdirs()
                        alvo.outputStream().use { zip.copyTo(it) }
                    }
                    entrada = zip.nextEntry
                }
            }
        }
        marcador.writeText(VERSAO)
        return destino
    }
}
```

```kotlin
// android/app/src/main/kotlin/com/example/ytdown/core/python/PyRuntime.kt
package com.example.ytdown.core.python

import android.content.Context
import java.io.File

/** Substitui o com.chaquo.python.Python. Uma instancia por processo. */
object PyRuntime {

    @Volatile private var iniciado = false

    private external fun nativeStart(home: String, path: String): Int
    private external fun nativeVersion(): String

    @Synchronized
    fun start(context: Context) {
        if (iniciado) return
        System.loadLibrary("pybridge")

        val stdlib = PythonAssets.ensureInstalled(context)
        // Modulos do app (ytdown.py e cia) e pacotes baixados em runtime pelo runtime.py.
        val appPython = File(context.filesDir, "app_python")
        val runtimePkgs = File(context.filesDir, "runtime_packages")

        val path = listOf(
            stdlib.absolutePath,
            File(stdlib, "lib-dynload").absolutePath,
            File(stdlib, "site-packages").absolutePath,
            appPython.absolutePath,
            runtimePkgs.absolutePath,
        ).joinToString(":")

        val rc = nativeStart(stdlib.absolutePath, path)
        check(rc == 0) { "falha ao iniciar CPython, rc=$rc" }
        iniciado = true
    }

    fun version(): String = nativeVersion()
}
```

Ligue o CMake em `android/app/build.gradle`, dentro de `android { }`:

```groovy
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
```

- [ ] **Passo 4: Rodar e ver passar**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*PyRuntimeTest*'
```

Esperado: PASS, `sys.version` começando com `3.14.7`. Se der `ModuleNotFoundError: encodings`, o `home` ou o
`module_search_paths` está errado — confirme via `adb logcat -s PyBridge python.stderr`.

- [ ] **Passo 5: Commit**

```bash
git add android/app/src/main/cpp android/app/src/main/kotlin/com/example/ytdown/core/python android/app/build.gradle android/app/src/androidTest
git commit -m "feat(python): ponte JNI iniciando CPython 3.14.7 oficial"
```

---

## Tarefa 5: Despacho `call(module, func, args)`

**Files:**
- Create: `android/app/src/main/python/_bridge.py`
- Create: `android/app/src/main/python/tests/test_bridge.py`
- Modify: `android/app/src/main/cpp/py_bridge.c`
- Modify: `android/app/src/main/kotlin/com/example/ytdown/core/python/PyRuntime.kt`

**Interfaces:**
- Produces: `PyRuntime.call(module: String, func: String, vararg args: Any?): String`.
  Consumido pelas Tarefas 7 e 8.

- [ ] **Passo 1: Escrever o teste de host que falha**

```python
# android/app/src/main/python/tests/test_bridge.py
import json
import unittest

import _bridge


class TestDespachante(unittest.TestCase):
    def test_chama_funcao_e_devolve_string(self):
        out = _bridge.dispatch("json", "dumps", json.dumps([{"a": 1}]))
        self.assertIsInstance(out, str)
        self.assertEqual('{"a": 1}', json.loads(out))

    def test_modulo_inexistente_vira_payload_de_erro(self):
        out = json.loads(_bridge.dispatch("nao_existe", "seja_o_que_for", "[]"))
        self.assertFalse(out["success"])
        self.assertEqual("bridge_import", out["stage"])

    def test_excecao_na_funcao_vira_payload_de_erro_com_traceback(self):
        out = json.loads(_bridge.dispatch("json", "loads", json.dumps(["{invalido"])))
        self.assertFalse(out["success"])
        self.assertEqual("bridge_call", out["stage"])
        self.assertIn("Expecting", out["error"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
cd android/app/src/main/python && PYTHONPATH=. python3 tests/test_bridge.py
```

Esperado: `ModuleNotFoundError: No module named '_bridge'`.

- [ ] **Passo 3: Escrever o despachante**

```python
# android/app/src/main/python/_bridge.py
"""Despachante unico chamado pelo JNI. Nada aqui conhece Android."""
import importlib
import json
import traceback


def _erro(mensagem, stage):
    return json.dumps({
        "success": False,
        "error": str(mensagem),
        "stage": stage,
        "retryable": False,
        "traceback": traceback.format_exc(),
    })


def dispatch(module_name, func_name, args_json):
    """Importa module_name, chama func_name(*args) e devolve string.

    As funcoes publicas ja retornam string JSON; repassamos sem reserializar.
    """
    try:
        modulo = importlib.import_module(module_name)
    except Exception as exc:
        return _erro(exc, "bridge_import")

    try:
        args = json.loads(args_json) if args_json else []
        resultado = getattr(modulo, func_name)(*args)
    except Exception as exc:
        return _erro(exc, "bridge_call")

    return resultado if isinstance(resultado, str) else json.dumps(resultado)
```

- [ ] **Passo 4: Rodar e ver passar**

```bash
cd android/app/src/main/python && PYTHONPATH=. python3 tests/test_bridge.py
```

Esperado: `OK` (3 testes).

- [ ] **Passo 5: Expor no JNI**

Acrescente em `py_bridge.c`:

```c
JNIEXPORT jstring JNICALL
Java_com_example_ytdown_core_python_PyRuntime_nativeCall(
        JNIEnv *env, jobject thiz, jstring j_mod, jstring j_fn, jstring j_args) {

    PyGILState_STATE gil = PyGILState_Ensure();
    const char *mod  = (*env)->GetStringUTFChars(env, j_mod,  NULL);
    const char *fn   = (*env)->GetStringUTFChars(env, j_fn,   NULL);
    const char *args = (*env)->GetStringUTFChars(env, j_args, NULL);

    jstring out = NULL;
    PyObject *bridge = PyImport_ImportModule("_bridge");
    if (bridge) {
        PyObject *r = PyObject_CallMethod(bridge, "dispatch", "sss", mod, fn, args);
        if (r) {
            const char *s = PyUnicode_AsUTF8(r);
            out = (*env)->NewStringUTF(env, s ? s : "");
            Py_DECREF(r);
        }
        Py_DECREF(bridge);
    }
    if (!out) {
        PyErr_Print();
        out = (*env)->NewStringUTF(env,
                "{\"success\":false,\"error\":\"falha no dispatch\",\"stage\":\"jni\"}");
    }

    (*env)->ReleaseStringUTFChars(env, j_mod,  mod);
    (*env)->ReleaseStringUTFChars(env, j_fn,   fn);
    (*env)->ReleaseStringUTFChars(env, j_args, args);
    PyGILState_Release(gil);
    return out;
}
```

E em `PyRuntime.kt`:

```kotlin
    private external fun nativeCall(module: String, func: String, argsJson: String): String

    /** Chama uma funcao Python. Retorna a string JSON que ela devolveu. */
    fun call(module: String, func: String, vararg args: Any?): String {
        check(iniciado) { "PyRuntime.start() nao foi chamado" }
        val json = org.json.JSONArray(args.toList()).toString()
        return nativeCall(module, func, json)
    }
```

- [ ] **Passo 6: Teste instrumentado do despacho**

```kotlin
    @Test
    fun call_devolve_json_da_funcao_python() {
        val out = PyRuntime.call("json", "dumps", listOf(mapOf("a" to 1)))
        assertTrue("saiu '$out'", out.contains("\"a\""))
    }
```

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*PyRuntimeTest*'
```

Esperado: PASS.

- [ ] **Passo 7: Commit**

```bash
git add android/app/src/main/python/_bridge.py android/app/src/main/python/tests/test_bridge.py android/app/src/main/cpp/py_bridge.c android/app/src/main/kotlin/com/example/ytdown/core/python/PyRuntime.kt android/app/src/androidTest
git commit -m "feat(python): despacho call(module,func,args) sobre a ponte JNI"
```

---

## Tarefa 6: Vendorizar os módulos do app e as dependências pip

**Files:**
- Modify: `scripts/fetch_python_android.sh`
- Modify: `android/app/build.gradle`
- Modify: `android/app/src/androidTest/.../PyRuntimeTest.kt`

**Interfaces:**
- Produces: `site-packages/` dentro de `stdlib.zip` com yt-dlp, mutagen, requests, beautifulsoup4,
  cloudscraper; e `app_python/` em `filesDir` com os `.py` do projeto.

- [ ] **Passo 1: Escrever o teste que falha**

```kotlin
    @Test
    fun yt_dlp_e_mutagen_importam() {
        val v = PyRuntime.call("_bridge", "dispatch",
                "yt_dlp.version", "__getattribute__", """["__version__"]""")
        assertTrue("retorno: $v", v.isNotBlank() && !v.contains("\"success\":false"))
    }

    @Test
    fun modulos_do_app_importam() {
        val out = PyRuntime.call("ytdown", "__getattribute__", "__all__")
        assertTrue("retorno: $out", out.contains("download_video"))
    }
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*PyRuntimeTest*'
```

Esperado: FALHA com `ModuleNotFoundError: No module named 'yt_dlp'`.

- [ ] **Passo 3: Instalar as dependências no prefix**

Acrescente ao final de `scripts/fetch_python_android.sh`, antes do `echo "OK"`:

```bash
# Todas as dependencias sao puro Python: pip --target resolve sem cross-compile.
DEPS=(yt-dlp mutagen requests beautifulsoup4 cloudscraper)

for host in "${!ALVOS[@]}"; do
  abi="${ALVOS[$host]}"
  SITE="$DEST/$abi/prefix/lib/python3.14/site-packages"
  mkdir -p "$SITE"
  python3 -m pip install --quiet --upgrade --target "$SITE" \
      --only-binary=:all: --python-version 3.14 --implementation cp \
      --abi none --platform any "${DEPS[@]}"
  find "$SITE" -name "__pycache__" -type d -prune -exec rm -rf {} +
  echo "==> $abi site-packages: $(du -sh "$SITE" | cut -f1)"
done
```

Se `--platform any` recusar algum pacote, ele tem binário — nesse caso o pacote não serve e precisa ser
substituído por alternativa pura.

Copie os módulos do app para `filesDir` no boot. Em `PythonAssets.kt`, acrescente:

```kotlin
    /** Copia os .py do app dos assets para filesDir/app_python. */
    fun installAppModules(context: Context): File {
        val destino = File(context.filesDir, "app_python")
        destino.mkdirs()
        context.assets.list("app_python")?.forEach { nome ->
            val alvo = File(destino, nome)
            context.assets.open("app_python/$nome").use { entrada ->
                alvo.outputStream().use { entrada.copyTo(it) }
            }
        }
        return destino
    }
```

E empacote os `.py` como asset, em `android/app/build.gradle`:

```groovy
tasks.register("packageAppPython", Copy) {
    from("src/main/python") { include "*.py" }
    into pyGenDir.map { it.dir("assets/app_python") }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
        .configureEach { dependsOn("packageAppPython") }
```

Chame `PythonAssets.installAppModules(context)` dentro de `PyRuntime.start()`, antes do `nativeStart`.

- [ ] **Passo 4: Rodar e ver passar**

```bash
./scripts/fetch_python_android.sh
cd android && ./gradlew connectedDebugAndroidTest --tests '*PyRuntimeTest*'
```

Esperado: PASS nos quatro testes.

- [ ] **Passo 5: Commit**

```bash
git add scripts/fetch_python_android.sh android/app/build.gradle android/app/src/main/kotlin/com/example/ytdown/core/python/PythonAssets.kt android/app/src/androidTest
git commit -m "feat(python): vendorizar yt-dlp, mutagen e modulos do app"
```

---

## Tarefa 7: Callback de progresso

`download_video` hoje recebe um objeto Kotlin (`PythonBridge.PythonProgressCallback`) e chama `.onProgress(int)`.
Sem Chaquopy não existe proxy automático Java↔Python. Trocamos por um módulo embutido em C.

**Files:**
- Modify: `android/app/src/main/cpp/py_bridge.c`
- Modify: `android/app/src/main/kotlin/com/example/ytdown/core/python/PyRuntime.kt`

- [ ] **Passo 1: Escrever o teste que falha**

```kotlin
    @Test
    fun progresso_chega_no_kotlin() {
        val recebidos = mutableListOf<Int>()
        PyRuntime.setProgressListener { pct -> recebidos.add(pct) }
        PyRuntime.call("_bridge", "emitir_progresso_de_teste", "[3]")
        assertEquals(listOf(0, 50, 100), recebidos)
    }
```

E em `_bridge.py`:

```python
def emitir_progresso_de_teste(quantidade):
    """Existe so para o teste instrumentado provar o caminho de callback."""
    import _android
    for pct in (0, 50, 100)[:quantidade]:
        _android.progress(pct)
    return json.dumps({"success": True})
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*PyRuntimeTest*'
```

Esperado: FALHA com `ModuleNotFoundError: No module named '_android'`.

- [ ] **Passo 3: Registrar o módulo embutido**

Em `py_bridge.c`, antes de `Py_InitializeFromConfig`:

```c
static JavaVM *g_vm = NULL;
static jobject g_listener = NULL;   // referencia global para o listener Kotlin

static PyObject *py_progress(PyObject *self, PyObject *args) {
    int pct;
    if (!PyArg_ParseTuple(args, "i", &pct)) return NULL;
    if (g_vm && g_listener) {
        JNIEnv *env;
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) == JNI_OK) {
            jclass cls = (*env)->GetObjectClass(env, g_listener);
            jmethodID m = (*env)->GetMethodID(env, cls, "onProgress", "(I)V");
            if (m) (*env)->CallVoidMethod(env, g_listener, m, pct);
        }
    }
    Py_RETURN_NONE;
}

static PyMethodDef metodos_android[] = {
    {"progress", py_progress, METH_VARARGS, "Reporta progresso ao Kotlin."},
    {NULL, NULL, 0, NULL}
};

static struct PyModuleDef modulo_android = {
    PyModuleDef_HEAD_INIT, "_android", NULL, -1, metodos_android
};

static PyObject *init_android(void) { return PyModule_Create(&modulo_android); }
```

No `nativeStart`, antes de `Py_InitializeFromConfig`:

```c
    (*env)->GetJavaVM(env, &g_vm);
    PyImport_AppendInittab("_android", init_android);
```

E o setter:

```c
JNIEXPORT void JNICALL
Java_com_example_ytdown_core_python_PyRuntime_nativeSetProgressListener(
        JNIEnv *env, jobject thiz, jobject listener) {
    if (g_listener) (*env)->DeleteGlobalRef(env, g_listener);
    g_listener = listener ? (*env)->NewGlobalRef(env, listener) : NULL;
}
```

Em `PyRuntime.kt`:

```kotlin
    fun interface ProgressListener { fun onProgress(pct: Int) }

    private external fun nativeSetProgressListener(listener: ProgressListener?)

    fun setProgressListener(listener: ProgressListener?) = nativeSetProgressListener(listener)
```

- [ ] **Passo 4: Rodar e ver passar**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*PyRuntimeTest*'
```

Esperado: PASS.

- [ ] **Passo 5: Adaptar o hook do yt-dlp em `download.py`**

O `download.py` recebe hoje um callback como argumento. Troque a chamada interna por `_android.progress(pct)`
com import tolerante, para o mesmo arquivo continuar rodando nos testes de host:

```python
try:
    import _android
except ImportError:                      # host / testes
    class _android:                      # noqa: N801
        @staticmethod
        def progress(_pct): pass
```

- [ ] **Passo 6: Rodar os testes de host para garantir que nada quebrou**

```bash
cd android/app/src/main/python
for t in tests/test_*.py; do PYTHONPATH=. python3 "$t" || echo "FALHOU: $t"; done
```

Esperado: todos `OK`.

- [ ] **Passo 7: Commit**

```bash
git add android/app/src/main/cpp android/app/src/main/kotlin/com/example/ytdown/core/python android/app/src/main/python/_bridge.py android/app/src/main/python/download.py android/app/src/androidTest
git commit -m "feat(python): callback de progresso via modulo embutido _android"
```

---

## Tarefa 8: Trocar os chamadores atrás de flag

**Files:**
- Modify: `android/app/src/main/kotlin/com/example/ytdown/core/business/YtDlpWrapper.kt`
- Modify: `android/app/src/main/kotlin/com/example/ytdown/core/artwork/PythonMetadataBridge.kt`
- Create: `android/app/src/main/kotlin/com/example/ytdown/core/python/PythonEngineFlag.kt`

**Interfaces:**
- Consumes: `PyRuntime.call(...)` da Tarefa 5, `PyRuntime.setProgressListener` da Tarefa 7.

Os 10 pontos de chamada são: `download_video`, `fetch_video_info`, `check_yt_dlp_update`,
`update_yt_dlp_if_needed`, `search_metadata`, `rewrite_file_metadata` (dois chamadores),
`read_file_metadata`, `embed_album_art`, `extract_embedded_artwork`, `extract_metadata_from_filename`.

- [ ] **Passo 1: Escrever o teste que falha, um chamador por vez**

Comece pelo mais simples, `fetch_video_info`:

```kotlin
    @Test
    fun fetch_video_info_pela_ponte_nova_devolve_json_com_success() {
        val out = PyRuntime.call("ytdown", "fetch_video_info",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                InstrumentationRegistry.getInstrumentation().targetContext.filesDir.absolutePath)
        val json = org.json.JSONObject(out)
        assertTrue("retorno: $out", json.has("success"))
    }
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*fetch_video_info*'
```

- [ ] **Passo 3: Implementar a flag e o caminho novo**

```kotlin
// android/app/src/main/kotlin/com/example/ytdown/core/python/PythonEngineFlag.kt
package com.example.ytdown.core.python

/** Enquanto as duas pontes coexistem. Remover na Tarefa 9. */
object PythonEngineFlag {
    @Volatile var useNative: Boolean = false
}
```

Em `YtDlpWrapper.kt`, cada chamada vira:

```kotlin
        val resultJson = if (PythonEngineFlag.useNative) {
            PyRuntime.call("ytdown", "fetch_video_info", url.value, appFilesDir)
        } else {
            Python.getInstance().getModule("ytdown")
                    .callAttr("fetch_video_info", url.value, appFilesDir).toString()
        }
```

- [ ] **Passo 4: Rodar e ver passar, com a flag ligada**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*fetch_video_info*'
```

- [ ] **Passo 5: Repetir os passos 1-4 para cada um dos 10 pontos de chamada**

Um commit por ponto. Não agrupe: se um quebrar, você quer saber qual.

- [ ] **Passo 6: Validação manual no aparelho, flag ligada**

Ligue `PythonEngineFlag.useNative = true` no `YTDownApplication.onCreate` e teste à mão:
download de vídeo, download de playlist com a tela desligada, edição de tag no `TagEditorDialog`,
importação de `cookies.txt` seguida de download de vídeo restrito, atualização do yt-dlp em Configurações.

- [ ] **Passo 7: Commit**

```bash
git commit -m "feat(python): rotear todos os chamadores pela ponte nativa sob flag"
```

---

## Tarefa 9: Remover o Chaquopy

Só depois da Tarefa 8 validada no aparelho.

**Files:**
- Modify: `android/build.gradle.kts`, `android/app/build.gradle`
- Delete: `android/app/src/main/kotlin/com/example/ytdown/PythonBridge.kt`
- Delete: `android/app/src/main/kotlin/com/example/ytdown/core/infrastructure/PythonEnvironment.kt`
- Modify: `android/app/src/main/kotlin/com/example/ytdown/MainActivity.kt`

- [ ] **Passo 1: Escrever o teste que falha**

```kotlin
    @Test
    fun nenhuma_classe_do_chaquopy_ficou_no_apk() {
        val e = runCatching { Class.forName("com.chaquo.python.Python") }.exceptionOrNull()
        assertTrue("Chaquopy ainda esta empacotado", e is ClassNotFoundException)
    }
```

- [ ] **Passo 2: Rodar e ver falhar**

```bash
cd android && ./gradlew connectedDebugAndroidTest --tests '*chaquopy*'
```

- [ ] **Passo 3: Remover**

- `android/build.gradle.kts`: apagar a linha `id("com.chaquo.python") version "17.0.0" apply false`.
- `android/app/build.gradle`: apagar `id "com.chaquo.python"` e o bloco `python { ... }` inteiro dentro de
  `defaultConfig`.
- `settings.gradle.kts`: apagar os dois `maven { url = uri("https://chaquo.com/maven") }`.
- Apagar `PythonBridge.kt` e `PythonEnvironment.kt`; trocar os usos de `PythonEnvironment` por
  `BinaryOrchestrator` (mesma API: `getAppFilesDir()` / `getNativeLibDir()`), ajustando `AppModule.kt`.
- `MainActivity.kt:52-75`: apagar o bloco `CHAQUOPY_DEBUG` e trocar `PythonBridge.initializePython(this)` por
  `PyRuntime.start(this)`.
- Apagar `PythonEngineFlag.kt` e todos os `if (PythonEngineFlag.useNative)`, deixando só o ramo nativo.

- [ ] **Passo 4: Rodar tudo**

```bash
cd android
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
cd app/src/main/python && for t in tests/test_*.py; do PYTHONPATH=. python3 "$t" || echo "FALHOU: $t"; done
```

Esperado: tudo verde.

- [ ] **Passo 5: Medir o APK e registrar**

```bash
cd android
unzip -l app/build/outputs/apk/debug/app-debug.apk | tail -1
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -E "lib/.*(python|pybridge)" \
  | awk '{s+=$1} END {printf "python nativo: %.1f MB\n", s/1048576}'
```

Anote o número real e compare com os 41,6 MB do Chaquopy. Se não melhorou, isso é informação — não motivo
para esconder.

- [ ] **Passo 6: Commit**

```bash
git add -A
git commit -m "refactor(python): remover Chaquopy, CPython 3.14.7 embutido no lugar"
```

---

## Riscos

- **R1 — GIL e threads.** O Chaquopy gerenciava o GIL sozinho. `nativeCall` usa `PyGILState_Ensure/Release`,
  mas download roda em `Dispatchers.IO` com várias corrotinas. Se aparecer travamento ou crash em download
  simultâneo, serialize as chamadas com um mutex em `PyRuntime.call` antes de investigar o C.
- **R2 — `lib-dynload` dentro do zip.** Módulos de extensão `.so` não carregam de dentro de um zip. Por isso
  `PythonAssets` extrai para `filesDir` em vez de deixar no asset comprimido. Se o boot ficar lento, mova só
  `lib-dynload` para `jniLibs` e deixe o resto zipado.
- **R3 — `runtime.py` e o novo `sys.path`.** O update do yt-dlp grava em `filesDir/runtime_packages` e faz
  `del sys.modules["yt_dlp"]`. Esse diretório já está no `module_search_paths` da Tarefa 4, mas precisa vir
  **antes** de `site-packages` para a versão baixada ganhar da vendorizada. Confirme a ordem.
- **R4 — Page size de 16 KB.** Android 15+ exige libs alinhadas a 16 KB. O testbed do CPython passa
  `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`. Se o app não subir em aparelho novo, é isso.
- **R5 — Manutenção.** A partir daqui, atualizar Python é rodar `fetch_python_android.sh` com outro
  `PY_VERSION` e revalidar a ponte. Ninguém faz isso por você.
