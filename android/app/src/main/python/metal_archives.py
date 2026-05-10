import json
import re
import time
from bs4 import BeautifulSoup
import cloudscraper

USER_AGENT = "YTDown/1.0 (Android Music Discovery; mailto:allanosan@email.com)"

MA_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    "Accept": "application/json, text/html, */*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.metal-archives.com/",
}


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

    return json.dumps({"success": False, "error": "Metal-Archives bloqueado. Tente pelo app."})


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

    return json.dumps({"success": False, "error": "Metal-Archives bloqueado. Tente pelo app."})


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

    return json.dumps({"success": False, "error": "Metal-Archives bloqueado. Tente pelo app."})