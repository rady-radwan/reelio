#!/usr/bin/env bash
# Builds a set of test songs and videos in many formats.
set -e
OUT=${1:-/tmp/media}
mkdir -p "$OUT/music" "$OUT/movies"
A="-f lavfi -i sine=frequency=440:duration=25"
ffmpeg -y -loglevel error $A -metadata title="Test Song MP3" -metadata artist="Reelio Band" -metadata album="Test Album" -c:a libmp3lame -b:a 128k "$OUT/music/song1.mp3"
ffmpeg -y -loglevel error -f lavfi -i sine=frequency=330:duration=25 -metadata title="Test Song OGG" -metadata artist="Ogg Artist" -c:a libvorbis "$OUT/music/song2.ogg"
ffmpeg -y -loglevel error -f lavfi -i sine=frequency=550:duration=25 "$OUT/music/song3.wav"
ffmpeg -y -loglevel error -f lavfi -i sine=frequency=660:duration=25 -metadata title="Test Song AAC" -c:a aac "$OUT/music/song4.m4a"
ffmpeg -y -loglevel error -f lavfi -i sine=frequency=770:duration=25 -c:a flac "$OUT/music/song5.flac"
ffmpeg -y -loglevel error -f lavfi -i sine=frequency=880:duration=25 -metadata title="Test Song WMA" -metadata artist="Windows Artist" -c:a wmav2 -b:a 128k "$OUT/music/song6.wma"
ffmpeg -y -loglevel error -f lavfi -i sine=frequency=990:duration=25 -c:a libopus "$OUT/music/song7.opus"
V="-f lavfi -i testsrc2=size=1280x720:rate=25:duration=30 -f lavfi -i sine=frequency=500:duration=30"
ffmpeg -y -loglevel error $V -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "$OUT/movies/clip1.mp4"
ffmpeg -y -loglevel error $V -c:v libx264 -pix_fmt yuv420p -c:a ac3 -shortest "$OUT/movies/clip2 subtitles.mkv"
cat > "$OUT/movies/clip2 subtitles.srt" <<'SRT'
1
00:00:00,500 --> 00:00:15,000
Reelio loaded this subtitle file
because it has the same name as the video

2
00:00:15,000 --> 00:00:30,000
Second line of subtitles
SRT
ffmpeg -y -loglevel error -f lavfi -i testsrc2=size=854x480:rate=25:duration=30 -f lavfi -i sine=frequency=600:duration=30 -c:v libvpx-vp9 -b:v 600k -deadline realtime -c:a libopus -shortest "$OUT/movies/clip3.webm"
ffmpeg -y -loglevel error -f lavfi -i testsrc2=size=720x720:rate=25:duration=30 -f lavfi -i sine=frequency=700:duration=30 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest "$OUT/movies/clip4 square.mov"
ffmpeg -y -loglevel error -f lavfi -i testsrc2=size=640x360:rate=25:duration=30 -f lavfi -i sine=frequency=800:duration=30 -c:v wmv2 -b:v 800k -c:a wmav2 -shortest "$OUT/movies/clip5.wmv"
ffmpeg -y -loglevel error -f lavfi -i testsrc2=size=640x360:rate=25:duration=30 -f lavfi -i sine=frequency=900:duration=30 -c:v mpeg4 -c:a libmp3lame -shortest "$OUT/movies/clip6.avi"
ls -la "$OUT/music" "$OUT/movies"
