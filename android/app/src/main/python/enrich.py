import json
import os
import time
import urllib.request
import urllib.parse

CREDENTIALS_FILE = os.path.join(os.path.dirname(__file__), "credentials.json")

def _get_credentials():
    if not os.path.exists(CREDENTIALS_FILE):
        return None, None
    with open(CREDENTIALS_FILE, 'r') as f:
        data = json.load(f)
        return data.get("musicbrainz_client_id"), data.get("musicbrainz_client_secret")

def _search_metadata(artist, track_name):
    client_id, _ = _get_credentials()
    base_url = "https://musicbrainz.org/ws/2/recording/"
    
    # Query: artist AND recording
    query = f'artist:"{artist}" AND recording:"{track_name}"'
    params = {
        "query": query,
        "fmt": "json",
        "limit": 1
    }
    url = f"{base_url}?{urllib.parse.urlencode(params)}"
    
    headers = {
        'User-Agent': 'YTDown/1.0 ( allanosan@email.com )',
        'Accept': 'application/json'
    }
    
    try:
        req = urllib.request.Request(url, headers=headers)
        # MusicBrainz rate limit: 1 request per second
        time.sleep(1.1)
        
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode())
            recordings = data.get("recordings", [])
            if not recordings:
                return None
            
            rec = recordings[0]
            # Extrair artista e album (se disponível)
            artist_name = rec.get("artist-credit", [{}])[0].get("name")
            releases = rec.get("releases", [])
            album_name = releases[0].get("title") if releases else "YTDown"
            
            result = {
                "artist": artist_name,
                "album": album_name,
                "title": rec.get("title")
            }
            # ✅ FIX: retorna JSON string, não dict Python.
            # dict.toString() em Chaquo usa repr Python (aspas simples) que quebra JSONObject()
            return json.dumps(result)
    except Exception as e:
        print(f"Erro na busca MusicBrainz: {e}")
        return None
