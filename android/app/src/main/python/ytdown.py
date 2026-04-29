"""
YTDown - Python module for Chaquo SDK
Entrypoint que delega a lógica para módulos auxiliares.
"""

from download import download_video
from fetch import fetch_video_info
from metadata import rewrite_file_metadata
from runtime import check_yt_dlp_update, update_yt_dlp_if_needed

__all__ = [
    "download_video",
    "fetch_video_info",
    "rewrite_file_metadata",
    "check_yt_dlp_update",
    "update_yt_dlp_if_needed",
]
