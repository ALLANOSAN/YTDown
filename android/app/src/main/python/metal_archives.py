import requests
import json
import re
from bs4 import BeautifulSoup
import io
from PIL import Image

def get_band_and_album_details(band_name, album_name=None):
    """
    Busca metadados completos (Gênero, Logo/Foto da Banda, Capa do Álbum).
    Faz fallback no Google se a imagem não for encontrada no Metal-Archives.
    """
    headers = {"User-Agent": "Mozilla/5.0"}
    
    try:
        # 1. Metal-Archives Search
        search_url = f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}"
        res = requests.get(search_url, headers=headers, timeout=10)
        data = res.json()
        if not data.get("aaData"):
            return json.dumps({"success": False, "error": "Banda não encontrada"})
            
        band_html = data["aaData"][0][0]
        band_id = re.search(r'/(\d+)"', band_html).group(1)
        band_url = f"https://www.metal-archives.com/bands/{band_name.replace(' ', '_')}/{band_id}"
        
        band_page = requests.get(band_url, headers=headers, timeout=10)
        soup = BeautifulSoup(band_page.text, 'html.parser')
        
        # Genre
        genre = "Metal"
        stats = soup.find('div', id='band_stats')
        if stats:
            genre = stats.find_all('dd')[1].text.strip()
        
        # Image (Band Logo/Photo)
        image_url = None
        photo_tag = soup.find('a', id='photo') or soup.find('a', id='logo')
        if photo_tag:
            image_url = photo_tag.get('href')
        
        # Fallback Google
        if not image_url:
            image_url = f"https://www.google.com/search?tbm=isch&q={band_name}+band+logo"
            
        return json.dumps({
            "success": True,
            "genre": genre,
            "band_image_url": image_url
        })
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})

def search_album_art(band_name, album_name):
    """
    Fallback para buscar capa do álbum se não houver no Metal-Archives
    """
    try:
        query = f"{band_name} {album_name} album cover"
        search_url = f"https://www.google.com/search?tbm=isch&q={query}"
        # Apenas retorna a URL para o Kotlin baixar e processar
        return json.dumps({"success": True, "album_image_url": search_url})
    except:
        return json.dumps({"success": False})
