import requests
import json
import re
from bs4 import BeautifulSoup

def get_band_details(band_name):
    """
    Fonte primária: Metal-Archives.
    Fallback para imagens: LastFM (via busca estruturada) ou Google Images.
    """
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }
    
    try:
        # 1. Buscar ID da banda no Metal-Archives
        search_url = f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}"
        search_response = requests.get(search_url, headers=headers, timeout=10)
        search_data = search_response.json()
        
        if not search_data.get("aaData"):
            return json.dumps({"success": False, "error": "Banda não encontrada"})
            
        first_result_html = search_data["aaData"][0][0]
        match = re.search(r'href="([^"]+)"', first_result_html)
        band_url = match.group(1)
        
        # 2. Extrair dados da página da banda
        band_page = requests.get(band_url, headers=headers, timeout=10)
        soup = BeautifulSoup(band_page.text, 'html.parser')
        
        # Gênero (Autoridade: Metal-Archives)
        genre = "Metal"
        stats = soup.find('div', id='band_stats')
        if stats:
            dd_tags = stats.find_all('dd')
            if len(dd_tags) >= 2:
                genre = dd_tags[1].text.strip()
        
        # Foto (Autoridade: Metal-Archives, Fallback: Google)
        image_url = None
        photo_tag = soup.find('a', id='photo')
        if photo_tag:
            image_url = photo_tag.get('href')
        
        # Fallback para imagem
        if not image_url:
            image_url = f"https://www.google.com/search?tbm=isch&q={band_name}+band+photo"
            
        return json.dumps({
            "success": True,
            "name": band_name,
            "genre": genre,
            "image_url": image_url
        })
        
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})
