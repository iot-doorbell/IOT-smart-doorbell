import asyncio
import json
import logging
import os
import subprocess
import threading
import time
import wave
from contextlib import asynccontextmanager
from queue import Queue

import cv2
import pygame
import pyaudio
import RPi.GPIO as GPIO
import uvicorn
import speech_recognition as sr
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from picamera2 import Picamera2
from langdetect import detect

# ===========================
# === Configuration Start ===
# ===========================
BUTTON_PIN = 16  # Single button for all functions
VIDEO_DEVICE = '/dev/video0'
PI_IP = 'raspberrypi.local'
HTTP_PORT = 5000

# Button timing thresholds (in seconds)
SHORT_PRESS_MAX = 1.5  # Maximum duration for short press

# Audio Settings
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 48000
CHUNK = 960
TIMEOUT = 30  # seconds
USB_INPUT_INDEX = 1  # mic device index

# Credentials
auth_data = {
    'id': '109492731201162729024',
    'username': 'dev405051',
    'password': '123456789',
    'email': 'dev405051@gmail.com'
}
# ===========================
# === Configuration End   ===
# =========================

# Initialize logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger('doorbell')

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup logic
    global loop
    loop = asyncio.get_event_loop()
    # Initialize pygame mixer for audio playback
    pygame.mixer.init()
    # Setup GPIO buttons
    GPIO.setmode(GPIO.BCM)
    GPIO.setup(BUTTON_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)
    GPIO.remove_event_detect(BUTTON_PIN)

    GPIO.add_event_detect(BUTTON_PIN, GPIO.BOTH, callback=button_callback, bouncetime=50)

    call_espeak("Ready to call")
    yield
    # Shutdown logic
    logger.info('Shutting down...')
    release_video_device()
    release_audio_device()
    picam2.stop()
    picam2.close()
    ding_stop_event.set()
    recording_stop_event.set()
    pygame.mixer.quit()
    audio.terminate()
    GPIO.cleanup()

# FastAPI setup
app = FastAPI(lifespan=lifespan)
app.add_event_handler("startup", lambda: logger.info("Starting up..."))
app.add_middleware(
    CORSMiddleware,
    allow_origins=['*'],
    allow_credentials=True,
    allow_methods=['*'],
    allow_headers=['*'],
)

# Shared state
enum_state = {'idle', 'awaiting_accept', 'streaming'}
state = 'idle'
state_lock = threading.Lock()
ding_stop_event = threading.Event()
recording = False
recording_stop_event = threading.Event()
button_press_time = 0  # Track when button was pressed

# Queues for WS with only one connection
signal_connections: WebSocket = None
text_connections: WebSocket = None

# Speech recognizer
recognizer = sr.Recognizer()

# === Hardware Init ===
# GPIO Button
# GPIO.setmode(GPIO.BCM)
# GPIO.setup(BUTTON_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)

# Release and init camera
def release_video_device(device: str = VIDEO_DEVICE):
    try:
        pids = subprocess.check_output(['lsof', '-t', device]).split()
        for pid in pids:
            subprocess.run(['kill', '-SIGTERM', pid])
            logger.info(f"Killed process {pid.decode()} holding {device}")
    except subprocess.CalledProcessError:
        pass
    except Exception as e:
        logger.warning(f"Error releasing video device: {e}")

def release_audio_device(device: str = USB_INPUT_INDEX):
    try:
        pids = subprocess.check_output(['lsof', '-t', device]).split()
        for pid in pids:
            subprocess.run(['kill', '-SIGTERM', pid])
            logger.info(f"Killed process {pid.decode()} holding {device}")
    except subprocess.CalledProcessError:
        pass
    except Exception as e:
        logger.warning(f"Error releasing audio device: {e}")

release_video_device()
release_audio_device()
picam2 = Picamera2()
config_portrait = picam2.create_preview_configuration(
    main={"format": "RGB888", "size": (480, 640)}
)

picam2.configure(config_portrait)
picam2.start()

# Audio devices
audio = pyaudio.PyAudio()

# ===========================
# === Utility Functions ====
# ===========================
def play_ring():
    try:
        if not pygame.mixer.get_init():
            pygame.mixer.init()
        
        pygame.mixer.music.load('/home/pi/doorbell/sound/ring.wav')
        pygame.mixer.music.play(-1)  # -1 means loop indefinitely
        
        # Wait until the ring should stop
        while not ding_stop_event.is_set():
            time.sleep(0.1)
            
        pygame.mixer.music.stop()
    except Exception as e:
        logger.error(f'Error playing ring sound: {e}')

def call_espeak(text: str):
    try:
        lang_code = detect(text)
        espeak_lang = 'en' if lang_code.startswith('en') else 'vi'
        subprocess.call(['espeak', '-v', f'{espeak_lang}+m3', '-s', '140', '-a', '200', text])
    except Exception as e:
        logger.error(f'Error with espeak: {e}')

def timeout_reached():
    global state
    with state_lock:
        if state == 'awaiting_accept':
            logger.info('Timeout reached, stopping ring')
            ding_stop_event.set()
            call_espeak('Timeout reached, please press the button again')
            state = 'idle'

# function to send message to client
async def send_message(ws: WebSocket, message: dict):
    message['type'] = 'server_to_app'
    json_message = json.dumps(message)
    logger.info(f"Sending message to client: {message}")
    await ws.send_text(json_message)

def start_call():
    global state
    
    with state_lock:
        if state != 'idle':
            return False
        state = 'awaiting_accept'
        if signal_connections is None:
            logger.info('No signal client connected')
            call_espeak('No signal client connected')
            state = 'idle'
            return False
    
    logger.info('Button pressed, calling clients')
    msg = {'status': 'calling', 'end_time': int(time.time() * 1000) + TIMEOUT * 1000}
    
    # send to client
    if loop.is_running():
        asyncio.run_coroutine_threadsafe(send_message(signal_connections, msg), loop)
    else:
        logger.warning('Event loop is not running, cannot send message')
    
    ding_stop_event.clear()
    threading.Thread(target=play_ring, daemon=True).start()
    threading.Timer(TIMEOUT, timeout_reached).start()
    return True

# ===========================
# === Audio to Text Functions ===
# ===========================
def record_audio():
    global recording
    frames = []
    
    # Open mic stream
    stream = audio.open(
        format=FORMAT,
        channels=CHANNELS,
        rate=RATE,
        input=True,
        input_device_index=USB_INPUT_INDEX,
        frames_per_buffer=CHUNK
    )
    
    logger.info("Recording started...")
    
    # Record until the button is released
    while recording and not recording_stop_event.is_set():
        try:
            data = stream.read(CHUNK, exception_on_overflow=False)
            frames.append(data)
        except Exception as e:
            logger.error(f"Error during recording: {e}")
            break
    
    logger.info("Recording stopped")
    
    # Close the stream
    stream.stop_stream()
    stream.close()
    
    # Save the recording temporarily
    if frames:
        temp_wav = "/tmp/recording.wav"
        with wave.open(temp_wav, 'wb') as wf:
            wf.setnchannels(CHANNELS)
            wf.setsampwidth(audio.get_sample_size(FORMAT))
            wf.setframerate(RATE)
            wf.writeframes(b''.join(frames))
        
        # Convert audio to text
        return temp_wav
    
    return None

def convert_audio_to_text(audio_file):
    try:
        with sr.AudioFile(audio_file) as source:
            audio_data = recognizer.record(source)
            text = recognizer.recognize_google(audio_data, language="vi-VN")
            logger.info(f"Converted text: {text}")
            return text
    except sr.UnknownValueError:
        logger.warning("Google Speech Recognition could not understand audio")
        return ""
    except sr.RequestError as e:
        logger.error(f"Could not request results from Google Speech Recognition service; {e}")
        return ""
    except Exception as e:
        logger.error(f"Error converting audio to text: {e}")
        return ""

def process_recording():
    global recording, text_connections
    
    # Start recording
    audio_file = record_audio()
    
    if audio_file and os.path.exists(audio_file):
        # Convert to text
        text = convert_audio_to_text(audio_file)
        if text == "":
            call_espeak("could not understand audio, please try again")
        # Send text to client
        if text_connections and loop.is_running() and text != "":
            msg = {'text': text, 'status': 'audio'}
            asyncio.run_coroutine_threadsafe(send_message(text_connections, msg), loop)
        
            recording=False
        # Clean up
        try:
            os.remove(audio_file)
        except:
            pass

# ===========================
# === Button Callback ======
# ===========================
def button_callback(channel):
    global state, button_press_time, recording
    
    button_state = GPIO.input(BUTTON_PIN)
    current_time = time.time()
    
    if button_state == GPIO.LOW:  # Button pressed
        button_press_time = current_time
        
        with state_lock:
            # If we're in streaming mode, start recording
            if state == 'streaming':
                if not recording:
                    logger.info("Button pressed in streaming mode - starting recording")
                    recording = True
                    recording_stop_event.clear()
                    threading.Thread(target=process_recording, daemon=True).start()
    
    else:  # Button released
        press_duration = current_time - button_press_time
        
        with state_lock:
            current_state = state
        
        # Short press while idle - initiate call
        if current_state == 'idle' and press_duration <= SHORT_PRESS_MAX:
            start_call()
        
        # In streaming mode, stop recording and process speech
        elif current_state == 'streaming' and recording:
            logger.info("Button released - stopping recording")
            recording = False
            recording_stop_event.set()
            time.sleep(0.1)
            # Reset for the next press
            button_press_time = 0

# ===========================
# === HTTP Endpoints =======
# ===========================
@app.post('/auth/login')
async def login(data: dict):
    if data.get('username') == auth_data['username'] and data.get('password') == auth_data['password']:
        return { 'message': 'Login successful', 'userId': auth_data['id'], 'userName': auth_data['username'], 'userEmail': auth_data['email'] }
    return JSONResponse({ 'message': 'Login failed' }, status_code=401)

@app.get('/video_feed')
async def video_feed(portrait: int = 1):
    async def gen():
        while True:
            frame = picam2.capture_array()
            ret, jpeg = cv2.imencode('.jpg', frame)
            if not ret:
                continue
            yield (b'--frame\r\n'
                   b'Content-Type: image/jpeg\r\n\r\n' + jpeg.tobytes() + b'\r\n')
            await asyncio.sleep(0.01)
    return StreamingResponse(gen(), media_type='multipart/x-mixed-replace;boundary=frame')

@app.get('/stop_feed')
async def stop_feed():
    return {"message": "Video feed stopped successfully."}
# ===========================
# === WebSocket Endpoints ==
# ===========================
@app.websocket('/signal')
async def signal_ws(ws: WebSocket):
    global state, signal_connections, recording
    await ws.accept()
    signal_connections = ws
    logger.info('Signal client connected')
    
    try:
        while True:
            data = await ws.receive_text()
            msg = json.loads(data)
            t = msg.get('type')
            
            if t == 'register':
                ws.client_id = msg.get('role')
                logger.info(f"Client registered: {ws.client_id}")
            
            elif t == 'app_to_server':
                if msg.get('status') == 'accept':
                    with state_lock:
                        state = 'streaming'
                    ding_stop_event.set()
                    call_espeak('Call accepted')
                    logger.info('Starting call')
                    await send_message(ws, {'type': 'start'})
                
                elif msg.get('status') == 'end':
                    with state_lock:
                        state = 'idle'
                    ding_stop_event.set()
                    recording_stop_event.set()
                    recording = False
                    call_espeak('Call ended')
                    await send_message(ws, {'type': 'stop'})
            
            await asyncio.sleep(0.5)
    
    except WebSocketDisconnect:
        signal_connections = None
        with state_lock:
            state = 'idle'
        ding_stop_event.set()
        recording_stop_event.set()
        recording = False
        logger.info('Signal client disconnected')

@app.websocket('/text')
async def text_ws(ws: WebSocket):
    global state, text_connections
    await ws.accept()
    text_connections = ws
    logger.info('Text client connected')
    
    try:
        while True:
            data = await ws.receive_text()
            msg = json.loads(data)
            
            with state_lock:
                if state != 'streaming':
                    continue
            
            if msg.get('type') == 'app_to_server' and msg.get('text'):
                # Speak the received text
                received_text = msg.get('text')
                logger.info(f"Received text from client: {received_text}")
                
                # Use espeak to speak the text
                call_espeak(received_text)
            
            await asyncio.sleep(0.1)
    
    except WebSocketDisconnect:
        text_connections = None
        logger.info('Text client disconnected')

if __name__ == '__main__':
    uvicorn.run(app, host='0.0.0.0', port=HTTP_PORT)

# lt --port 5000 --subdomain mydoorbell
# sudo nano /etc/systemd/system/startdoorbell.service
# sudo nano /etc/systemd/system/localtunnel.service