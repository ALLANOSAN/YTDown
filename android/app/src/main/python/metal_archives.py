import requests
import json
import re
from bs4 import BeautifulSoup

def get_similar_bands(band_name):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }
    
    try:
        search_url = f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}"
        search_response = requests.get(search_url, headers=headers, timeout=10)
        search_response.raise_for_status()
        search_data = search_response.json()
        
        if not search_data.get("aaData") or len(search_data["aaData"]) == 0:
            return json.dumps({"success": False, "error": "Banda não encontrada no Metal-Archives"})
            
        first_result_html = search_data["aaData"][0][0]
        match = re.search(r'/(\d+)"', first_result_html)
        if not match:
            return json.dumps({"success": False, "error": "Não foi possível extrair o ID da banda"})
            
        band_id = match.group(1)
        
        similar_url = f"https://www.metal-archives.com/band/ajax-recommendations/id/{band_id}?showMoreSimilar=1"
        similar_response = requests.get(similar_url, headers=headers, timeout=10)
        similar_response.raise_for_status()
        
        soup = BeautifulSoup(similar_response.text, 'html.parser')
        rows = soup.find_all('tr')
        
        if len(rows) <= 1:
             return json.dumps({"success": True, "source_band": band_name, "bands": []})

        similar_bands = []
        for row in rows[1:]:
            cols = row.find_all('td')
            if len(cols) >= 4:
                similar_bands.append({
                    "name": cols[0].text.strip(),
                    "genre": cols[1].text.strip(),
                    "country": cols[2].text.strip(),
                    "score": cols[3].text.strip()
                })
        
        return json.dumps({
            "success": True, 
            "source_band": band_name,
            "bands": similar_bands
        })
        
    except Exception as e:
        return json.dumps({"success": False, "error": f"Erro na descoberta: {str(e)}"})

def get_band_details(band_name):
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    }
    
    try:
        search_url = f"https://www.metal-archives.com/search/ajax-band-search/?field=name&query={band_name}"
        search_response = requests.get(search_url, headers=headers, timeout=10)
        search_data = search_response.json()
        
        if not search_data.get("aaData"):
            return json.dumps({"success": False, "error": "Banda não encontrada"})
            
        first_result_html = search_data["aaData"][0][0]
        match = re.search(r'href="([^"]+)"', first_result_html)
        if not match:
            return json.dumps({"success": False, "error": "URL da banda não encontrada"})
            
        band_url = match.group(1)
        band_page = requests.get(band_url, headers=headers, timeout=10)
        soup = BeautifulSoup(band_page.text, 'html.parser')
        
        stats = soup.find('div', id='band_stats')
        genre = "Metal"
        if stats:
            dd_tags = stats.find_all('dd')
            if len(dd_tags) >= 2:
                genre = dd_tags[1].text.strip()
        
        image_url = None
        image_tag = soup.find('a', id='photo') or soup.find('a', id='logo')
        if image_tag:
            image_url = image_tag.get('href')
            
        return json.dumps({
            "success": True,
            "name": band_name,
            "genre": genre,
            "image_url": image_url
        })
        
    except Exception as e:
        return json.dumps({"success": False, "error": str(e)})
