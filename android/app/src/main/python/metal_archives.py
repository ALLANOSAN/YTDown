import requests
import json
import re
from bs4 import BeautifulSoup

def get_band_details(band_name, album_name=None):
    """
    Fonte primária: Metal-Archives.
    Fallback para imagens: Google Images se a imagem do MA falhar.
    """
    headers = {"User-Agent": "Mozilla/5.0"}
    
    try:
        # 1. Buscar ID da banda
        search_url = f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}"
        res = requests.get(search_url, headers=headers, timeout=10)
        data = res.json()
        if not data.get("aaData"): return json.dumps({"success": False, "error": "Banda não encontrada"})
            
        band_html = data["aaData"][0][0]
        match = re.search(r'href="([^"]+)"', band_html)
        band_url = match.group(1)
        
        band_page = requests.get(band_url, headers=headers, timeout=10)
        soup = BeautifulSoup(band_page.text, 'html.parser')
        
        # Gênero
        genre = "Metal"
        stats = soup.find('div', id='band_stats')
        if stats:
            dd_tags = stats.find_all('dd')
            if len(dd_tags) >= 2:
                genre = dd_tags[1].text.strip()
        
        # Foto da Banda (Logo/Photo)
        image_url = None
        photo_tag = soup.find('a', id='photo') or soup.find('a', id='logo')
        if photo_tag:
            image_url = photo_tag.get('href')
        
        # Fallback inteligente (Google)
        if album_name:
            image_url = f"https://www.google.com/search?tbm=isch&q={band_name}+{album_name}+album+cover"
        elif not image_url:
            image_url = f"https://www.google.com/search?tbm=isch&q={band_name}+band+logo"
            
        return json.dumps({
            "success": True,
            "name": band_name,
            "genre": genre,
            "image_url": image_url
        })
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})

def get_band_albums(band_name):
    """
    Lista todos os álbuns de uma banda.
    """
    headers = {"User-Agent": "Mozilla/5.0"}
    try:
        search_url = f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}"
        res = requests.get(search_url, headers=headers, timeout=10)
        data = res.json()
        if not data.get("aaData"): return json.dumps({"success": False})
            
        band_html = data["aaData"][0][0]
        band_id = re.search(r'/(\d+)"', band_html).group(1)
        discography_url = f"https://www.metal-archives.com/band/discography/id/{band_id}/tab/all"
        
        disc_page = requests.get(discography_url, headers=headers, timeout=10)
        soup = BeautifulSoup(disc_page.text, 'html.parser')
        
        albums = []
        for row in soup.find_all('tr')[1:]:
            cols = row.find_all('td')
            if len(cols) >= 3:
                albums.append({"name": cols[0].text.strip(), "year": cols[1].text.strip()})
        
        return json.dumps({"success": True, "albums": albums})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})

def get_similar_bands(band_name):
    headers = {"User-Agent": "Mozilla/5.0"}
    try:
        search_url = f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}"
        res = requests.get(search_url, headers=headers, timeout=10)
        data = res.json()
        if not data.get("aaData"): return json.dumps({"success": False})
        band_id = re.search(r'/(\d+)"', data["aaData"][0][0]).group(1)
        
        similar_url = f"https://www.metal-archives.com/band/ajax-recommendations/id/{band_id}?showMoreSimilar=1"
        similar_res = requests.get(similar_url, headers=headers, timeout=10)
        soup = BeautifulSoup(similar_res.text, 'html.parser')
        
        bands = [{"name": r.find_all('td')[0].text.strip()} for r in soup.find_all('tr')[1:] if len(r.find_all('td')) >= 4]
        return json.dumps({"success": True, "bands": bands})
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})
