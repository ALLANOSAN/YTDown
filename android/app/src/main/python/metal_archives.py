import json
import re
import time
import urllib.request
import urllib.parse
import base64
from bs4 import BeautifulSoup
import cloudscraper
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

CLIENT_ID = "6I6IOxZVRQWohXUv9f263OPy9CR1RPce"
CLIENT_SECRET = "BgROjVloaJJq6i0qlIGZGFa5m_CxIO2C"
MB_TOKEN = None
MB_TOKEN_EXPIRY = 0

MA_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Accept": "application/json, text/html, */*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.metal-archives.com/",
}


def _mb_get_token():
    global MB_TOKEN, MB_TOKEN_EXPIRY
    import time as _time
    if MB_TOKEN and _time.time() < MB_TOKEN_EXPIRY:
        return MB_TOKEN

    credentials = f"{CLIENT_ID}:{CLIENT_SECRET}"
    encoded = base64.b64encode(credentials.encode()).decode()

    data = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "client_id": CLIENT_ID,
        "client_secret": CLIENT_SECRET
    }).encode()

    req = urllib.request.Request(
        "https://musicbrainz.org/oauth2/token",
        data=data,
        headers={
            "Authorization": f"Basic {encoded}",
            "Content-Type": "application/x-www-form-urlencoded",
            "User-Agent": "YTDown/1.0 (Android Music Discovery; mailto:allanosan@email.com)"
        }
    )

    with urllib.request.urlopen(req, timeout=20) as resp:
        token_data = json.loads(resp.read().decode())
        MB_TOKEN = token_data["access_token"]
        MB_TOKEN_EXPIRY = _time.time() + token_data.get("expires_in", 3600) - 60
        return MB_TOKEN


def _mb_request(url, params=None):
    token = _mb_get_token()
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {token}",
        "Accept": "application/json",
        "User-Agent": "YTDown/1.0 (Android Music Discovery; mailto:allanosan@email.com)"
    })
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())


def _scraper():
    return cloudscraper.create_scraper(
        browser={"browser": "chrome", "platform": "windows", "desktop": True}
    )


def get_similar_bands(band_name):
    scraper = _scraper()
    bands = []

    try:
        res = scraper.get(
            f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}",
            headers=MA_HEADERS, timeout=20
        )
        if res.status_code == 200 and res.text.strip().startswith("{"):
            data = res.json()
            if data.get("aaData"):
                band_id = re.search(r'/(\d+)"', data["aaData"][0][0]).group(1)
                sr = scraper.get(
                    f"https://www.metal-archives.com/band/ajax-recommendations/id/{band_id}?showMoreSimilar=1",
                    headers=MA_HEADERS, timeout=20
                )
                for row in BeautifulSoup(sr.text, 'html.parser').find_all('tr')[1:]:
                    cols = row.find_all('td')
                    if len(cols) >= 4:
                        bands.append({
                            "name": cols[0].text.strip(),
                            "genre": cols[1].text.strip() or "Metal",
                            "country": cols[2].text.strip() or "",
                            "score": cols[3].text.strip() if len(cols) > 3 else ""
                        })
    except Exception:
        pass

    if bands:
        time.sleep(3)
        return json.dumps({"success": True, "bands": bands, "source": "metal-archives"})

    try:
        artists = _mb_request(
            "https://musicbrainz.org/ws/2/artist/",
            {"query": band_name, "limit": 1, "fmt": "json"}
        ).get("artists", [])

        if not artists:
            return json.dumps({"success": False, "error": f"Banda '{band_name}' nao encontrada"})

        mbid = artists[0]["id"]
        lookup = _mb_request(
            f"https://musicbrainz.org/ws/2/artist/{mbid}",
            {"inc": "tags,artist-rels", "fmt": "json"}
        )

        genre = next((t["name"] for t in lookup.get("tags", []) if t.get("name")), "Metal")
        similar = [
            {
                "name": r["target"]["artist"]["name"],
                "genre": genre,
                "country": r["target"]["artist"].get("country", "") or "",
                "score": ""
            }
            for r in lookup.get("relations", [])
            if r.get("type") == "similar to" and "artist" in r.get("target", {})
        ]

        if similar:
            return json.dumps({"success": True, "bands": similar[:20], "source": "musicbrainz"})

        return json.dumps({"success": False, "error": "Nenhuma banda similar encontrada"})
    except Exception as e:
        return json.dumps({"success": False, "error": f"Erro MusicBrainz: {e}"})


def get_band_details(band_name, album_name=None):
    scraper = _scraper()
    details = {}

    try:
        res = scraper.get(
            f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}",
            headers=MA_HEADERS, timeout=20
        )
        if res.status_code == 200 and res.text.strip().startswith("{"):
            data = res.json()
            if data.get("aaData"):
                match = re.search(r'href="([^"]+)"', data["aaData"][0][0])
                if match:
                    bp = scraper.get(match.group(1), headers=MA_HEADERS, timeout=20)
                    soup = BeautifulSoup(bp.text, 'html.parser')
                    stats = soup.find('div', id='band_stats')
                    if stats and len(stats.find_all('dd')) >= 2:
                        details["genre"] = stats.find_all('dd')[1].text.strip()
                    photo = soup.find('a', id='photo') or soup.find('a', id='logo')
                    if photo:
                        details["image_url"] = photo.get('href')
    except Exception:
        pass

    if details:
        return json.dumps({"success": True, "name": band_name, **details})

    try:
        artists = _mb_request(
            "https://musicbrainz.org/ws/2/artist/",
            {"query": band_name, "limit": 1, "fmt": "json"}
        ).get("artists", [])

        if not artists:
            return json.dumps({"success": False, "error": f"Banda '{band_name}' nao encontrada"})

        mbid = artists[0]["id"]
        lookup = _mb_request(
            f"https://musicbrainz.org/ws/2/artist/{mbid}",
            {"inc": "tags,url-rels", "fmt": "json"}
        )

        genre = next((t["name"] for t in lookup.get("tags", []) if t.get("name")), "Metal")

        return json.dumps({
            "success": True,
            "name": band_name,
            "genre": genre,
            "image_url": f"https://www.google.com/search?tbm=isch&q={band_name}+band+logo",
            "source": "musicbrainz"
        })
    except Exception as e:
        return json.dumps({"success": False, "error": f"Erro MusicBrainz: {e}"})


def get_band_albums(band_name):
    scraper = _scraper()
    albums = []

    try:
        res = scraper.get(
            f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}",
            headers=MA_HEADERS, timeout=20
        )
        if res.status_code == 200 and res.text.strip().startswith("{"):
            data = res.json()
            if data.get("aaData"):
                band_id = re.search(r'/(\d+)"', data["aaData"][0][0]).group(1)
                dp = scraper.get(
                    f"https://www.metal-archives.com/band/discography/id/{band_id}/tab/all",
                    headers=MA_HEADERS, timeout=20
                )
                for row in BeautifulSoup(dp.text, 'html.parser').find_all('tr')[1:]:
                    cols = row.find_all('td')
                    if len(cols) >= 3:
                        albums.append({"name": cols[0].text.strip(), "year": cols[1].text.strip()})
    except Exception:
        pass

    if albums:
        time.sleep(3)
        return json.dumps({"success": True, "albums": albums, "source": "metal-archives"})

    try:
        artists = _mb_request(
            "https://musicbrainz.org/ws/2/artist/",
            {"query": band_name, "limit": 1, "fmt": "json"}
        ).get("artists", [])

        if not artists:
            return json.dumps({"success": False, "error": f"Banda '{band_name}' nao encontrada"})

        mbid = artists[0]["id"]
        data = _mb_request(
            "https://musicbrainz.org/ws/2/release-group",
            {"artist": mbid, "type": "album|studio", "limit": 50, "fmt": "json", "inc": "genres"}
        )

        for rg in data.get("release-groups", []):
            year = rg.get("first-release-date", "")[:4] or "?"
            albums.append({"name": rg.get("title", "Unknown"), "year": year})

        return json.dumps({"success": True, "albums": albums[:50], "source": "musicbrainz"})
    except Exception as e:
        return json.dumps({"success": False, "error": f"Erro MusicBrainz: {e}"})
