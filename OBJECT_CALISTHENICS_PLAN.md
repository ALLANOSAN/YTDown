# Plano de Implementação dos Object Calisthenics

Este plano descreve como aplicar os 9 princípios do Object Calisthenics ao projeto APPDOWNLOADYOUTUBE, focando inicialmente no arquivo `lib/services/download_service.dart` que foi analisado.

## Princípios do Object Calisthenics

1. **Only one level of indentation per method**
2. **Don't use the ELSE keyword**
3. **Wrap all primitives and strings**
4. **First-class collections**
5. **One dot per line**
6. **Don't abbreviate**
7. **Keep all entities small**
8. **No classes with more than two instance variables**
9. **No getters/setters/properties**

## Análise do código atual

Após análise do `lib/services/download_service.dart`, foram identificadas as seguintes áreas de foco:

### Métodos complexos que violam múltiplos princípios:
- `_executeDownload` (linhas 354-496): muito longo, múltiplos níveis de indentação, uso de ELSE, múltiplos dots
- `rewriteDownloadMetadata` (linhas 701-974): extremamente longo, múltiplos níveis de indentação
- `_prepareAndStartDownload` (linhas 320-352): múltiplos níveis de indentação
- `_applyMetadataFallbacks` (linhas 499-577): complexo, múltiplos níveis de indentação

### Classes com muitos atributos:
- `DownloadService`: possui muitos atributos (mais de 2)
- Classes de resultado como `MetadataBatchRepairResult`, `ArtworkBatchApplyResult`: têm múltiplos atributos

## Plano de Implementação

### Fase 1: Preparação
1. Criar branc no git para o trabalho
2. Identificar e extrair constantes mágicas
3. Criar wrappers para tipos primitivos usados frequentemente

### Fase 2: Aplicação dos Princípios
Aplicaremos os princípios em ordem de impacto, começando pelos que trazem maior benefício imediato.

### Fase 3: Refatoração específica por princípio

## Detalhamento das Tarefas

### Tarefa 1: Only one level of indentation per method
- Extrair blocos condicionais e de loop para métodos separados
- Aplicar ao método `_executeDownload` primeiro
- Depois aplicar a `_prepareAndStartDownload`, `rewriteDownloadMetadata`

### Tarefa 2: Don't use the ELSE keyword
- Substituir if/else por early returns
- Usar operadores ternários quando apropriado
- Aplicar inicialmente aos métodos com maior quantidade de ELSE

### Tarefa 3: Wrap all primitives and strings
- Criar classes wrapper para:
  - DownloadId (String)
  - FilePath (String)
  - Url (String)
  - Title (String)
  - Artist (String)
  - Album (String)
  - Format (String)
  - Quality (String)
- Começar pelos tipos usados em múltiplos locais

### Tarefa 4: First-class collections
- Substituir listas e mapas diretos por coleções especializadas
- Exemplo: Criar classe `DownloadItemList` que encapsula List<DownloadItem>
- Aplicar às coleções de cache e listas de downloads

### Tarefa 5: One dot per line
- Eliminar encadeamento de métodos
- Armazenar resultados intermediários em variáveis com nomes descritivos
- Focar inicialmente em métodos com chamadas em cadeia como `item.copyWith(...)`

### Tarefa 6: Don't abbreviate
- Revisar nomes de variáveis e métodos abreviados
- Exemplos no código: `ext`, `img`, `dir` (embora alguns sejam aceitáveis em contexto)

### Tarefa 7: Keep all entities small
- Quebrar métodos grandes em menores
- Limitar métodos a no máximo 10-15 linhas
- Aplicar primeiro aos métodos identificados como complexos

### Tarefa 8: No classes with more than two instance variables
- Refatorar DownloadService para reduzir atributos
- Agrupar atributos relacionados em classes auxiliares
- Exemplo: Criar classe para gerenciamento de cache de arte, outra para controle de locks

### Tarefa 9: No getters/setters/properties
- Substituir acesso direto a atributos por métodos com significado
- No Dart, isso se traduz em evitar expôr estado interno e usar mensagens em vez de getters
- Focar em encapsular melhor o estado da classe

## Priorização

Com base na análise, recomenda-se seguir esta ordem:

1. **Tarefa 7 (Keep all entities small)** - Quebrar métodos grandes traz benefícios imediatos de legibilidade
2. **Tarefa 1 (Only one level of indentation)** - Reduz complexidade ciclomática
3. **Tarefa 2 (Don't use ELSE)** - Simplifica fluxos de controle
4. **Tarefa 5 (One dot per line)** - Torna dependências mais explícitas
5. **Tarefa 3 (Wrap primitives)** - Melhora semântica e reduz erros
6. **Tarefa 4 (First-class collections)** - Encapsula comportamento de coleções
7. **Tarefa 6 (Don't abbreviate)** - Melhora clareza (menor impacto)
8. **Tarefa 8 (Max 2 instance vars)** - Requer refatoração estrutural maior
9. **Tarefa 9 (No getters/setters)** - Reposta de pensamento sobre orientação a objetos

## Próximos Passos

1. Criar branch no repositório
2. Implementar tarefa 7 no método `_executeDownload`
3. Revisar com o usuário
4. Prosseguir para próxima tarefa