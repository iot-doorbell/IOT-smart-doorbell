import signal
import subprocess
import threading
import time

import numpy as np
import paho.mqtt.client as mqtt
import pygame
import tflite_runtime.interpreter as tflite
from gpiozero import Button
from picamera2 import Picamera2

MQTT_HOST = '730247187d394eafac50bfb46350c7ec.s1.eu.hivemq.cloud'
MQTT_PORT = 8883
MQTT_USER = 'doorbell'
MQTT_PASS = 'Doorbell123'
TOPIC_CMD = 'app_to_doorbell/cmd'
TOPIC_STATUS = 'doorbell_to_app/cmd'
DEVICE_ID = 'c85091fa-a7af-41b2-8d9b-c366cf4a5cbe'
FPS = 30
WIDTH = 960
HEIGHT = 680

BUTTON_PIN = 16
RING_WAV = '/home/pi/doorbell/ring.wav'

TFLITE_MODEL_PATH = '/home/pi/doorbell/models/detect.tflite'
interpreter = tflite.Interpreter(model_path=TFLITE_MODEL_PATH)
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

state_lock = threading.Lock()
ring_thread = None
ring_stop_evt = threading.Event()
timeout_timer = None
webrtc_proc = None
state = 'idle'  # idle, calling, streaming
picam2 = None
button = Button(BUTTON_PIN, pull_up=True)

def speak(text):
    # Phát trực tiếp bằng espeak, không dùng --stdout để đảm bảo âm thanh ra loa
    subprocess.run([
        'espeak',
        '-v', 'en+m3',  # giọng nam tiếng Anh
        '-s', '140',    # tốc độ nói
        '-a', '200',    # âm lượng
        '-g', '5',      # khoảng dừng giữa các từ
        text
    ], check=True)

def on_connect(client, userdata, flags, rc):
    print("Connected with result code", rc)
    client.subscribe(TOPIC_CMD)

def on_message(client, userdata, msg):
    global state, timeout_timer
    print(f"Received: {msg.topic} {msg.payload.decode()}")
    try:
        import json
        data = json.loads(msg.payload.decode())
        status = data.get('status')
        if status == 'accept':
            handle_accept()
        elif status == 'end':
            handle_end()
    except Exception as e:
        print(f"Error parsing MQTT message: {e}")

def on_disconnect(client, userdata, rc):
    print(f"Disconnected with result code {rc}, trying to reconnect...")
    try:
        client.reconnect()
    except Exception as e:
        print(f"Reconnect failed: {e}")
        time.sleep(5)
        try:
            client.reconnect()
        except Exception as e:
            print(f"Second reconnect failed: {e}")

def play_ring():
    pygame.mixer.init()
    pygame.mixer.music.load(RING_WAV)
    pygame.mixer.music.play(-1)
    while not ring_stop_evt.is_set():
        pygame.time.wait(100)
    pygame.mixer.music.stop()
    pygame.mixer.quit()

def start_ring():
    global ring_thread
    ring_stop_evt.clear()
    ring_thread = threading.Thread(target=play_ring, daemon=True)
    ring_thread.start()

def stop_ring():
    global ring_thread
    ring_stop_evt.set()
    if ring_thread and ring_thread.is_alive():
        ring_thread.join(timeout=2)
    try:
        pygame.mixer.music.stop()
        pygame.mixer.quit()
    except Exception:
        pass
    ring_thread = None

def start_webrtc():
    global webrtc_proc
    import subprocess
    if webrtc_proc and webrtc_proc.poll() is None:
        return
    webrtc_proc = subprocess.Popen([
        '/home/pi/doorbell/pi-webrtc',
        '--camera=libcamera:0',
        f'--fps={FPS}',
        f'--width={WIDTH}',
        f'--height={HEIGHT}',
        '--use-mqtt',
        f'--mqtt-host={MQTT_HOST}',
        f'--mqtt-port={MQTT_PORT}',
        f'--mqtt-username={MQTT_USER}',
        f'--mqtt-password={MQTT_PASS}',
        f'--uid={DEVICE_ID}',
        '--hw-accel'
    ], stdout=subprocess.DEVNULL)

def stop_webrtc():
    global webrtc_proc
    if webrtc_proc and webrtc_proc.poll() is None:
        webrtc_proc.terminate()
        try:
            webrtc_proc.wait(timeout=2)
        except Exception:
            webrtc_proc.kill()
    webrtc_proc = None

def reset():
    global state, timeout_timer
    print("[reset] Called")
    stop_ring()
    stop_webrtc()
    with state_lock:
        state = 'idle'
    if timeout_timer:
        timeout_timer.cancel()
        timeout_timer = None
    print(f"Resetting state to {state}")
    client.publish(TOPIC_STATUS, '{"status": "idle"}', qos=1)

def handle_accept():
    global state, timeout_timer
    stop_ring()
    with state_lock:
        state = 'streaming'
    if timeout_timer:
        timeout_timer.cancel()
        timeout_timer = None
    try:
        picam2.close()
    except Exception:
        pass
    start_webrtc()

def handle_end():
    print("[handle_end] Called")
    speak("End call.")
    reset()

def handle_timeout():
    global timeout_timer
    print("[handle_timeout] Called")
    try:
        with state_lock:
            if state == 'calling':
                client.publish(TOPIC_STATUS, '{"status": "idle", "msg": "Call timed out"}', qos=1)
                speak("Call timed out.")
        reset()
        timeout_timer = None
    except Exception as e:
        print(f"[handle_timeout] Exception: {e}")

def button_callback():
    global state, timeout_timer
    with state_lock:
        print(f"Button pressed, current state: {state}")
        if state == 'idle':
            state = 'calling'
            print("State changed to 'calling'")
            start_ring()
            client.publish(TOPIC_STATUS, '{"status": "calling"}', qos=1)
            if timeout_timer:
                timeout_timer.cancel()
            timeout_timer = threading.Timer(30, handle_timeout)
            timeout_timer.start()
        else:
            print("Button press ignored, not in idle state.")

button.when_pressed = button_callback


def detect_person_mobilenet_v1_picam():
    detected = False
    for _ in range(3):
        frame = picam2.capture_array()
        print("Frame shape:", frame.shape)
        img = frame
        input_data = np.expand_dims(img, axis=0).astype(np.uint8)
        interpreter.set_tensor(input_details[0]['index'], input_data)
        interpreter.invoke()
        boxes = interpreter.get_tensor(output_details[0]['index'])[0]
        classes = interpreter.get_tensor(output_details[1]['index'])[0]
        scores = interpreter.get_tensor(output_details[2]['index'])[0]
        print("Classes:", classes)
        print("Scores:", scores)
        for i in range(len(scores)):
            if scores[i] > 0.6 and int(classes[i]) == 0:
                detected = True
                break
        if detected:
            break
    return detected

def person_detection_loop():
    global picam2
    was_person_present = False
    camera_open = False
    try:
        while True:
            with state_lock:
                current_state = state
            if current_state == 'idle':
                if not camera_open:
                    try:
                        picam2 = Picamera2()
                        config = picam2.create_still_configuration(
                            main={"size": (300, 300), "format": "BGR888"}
                        )
                        picam2.configure(config)
                        picam2.start()
                        camera_open = True
                        print("picam2 started in idle")
                    except Exception as e:
                        print(f"Error starting picam2: {e}")
                        time.sleep(2)
                        continue
                person_now = detect_person_mobilenet_v1_picam()
                print(f"[person_detection] {person_now}")
                if person_now and not was_person_present:
                    print("[person_detection] Person detected!")
                    client.publish(TOPIC_STATUS, '{"status": "person_detected"}', qos=1)
                    was_person_present = True
                elif not person_now:
                    print("[person_detection] No person detected.")
                    was_person_present = False
                time.sleep(1)
            else:
                if camera_open:
                    try:
                        picam2.close()
                        camera_open = False
                        print("picam2 closed (not idle)")
                    except Exception as e:
                        print(f"Error closing picam2: {e}")
                time.sleep(2)
    finally:
        if camera_open:
            picam2.close()

def handle_exit(signum, frame):
    print("Exiting... (signal received)")
    try:
        client.disconnect()
    except Exception:
        pass
    exit(0)

signal.signal(signal.SIGINT, handle_exit)
signal.signal(signal.SIGTERM, handle_exit)

client = mqtt.Client()
client.username_pw_set(MQTT_USER, MQTT_PASS)
client.tls_set()  # Sử dụng chứng chỉ hệ thống mặc định
client.on_connect = on_connect
client.on_message = on_message
client.on_disconnect = on_disconnect

client.connect(MQTT_HOST, MQTT_PORT, 60)

if __name__ == "__main__":
    speak("Ready to call.")
    threading.Thread(target=person_detection_loop, daemon=True).start()
    client.loop_forever()