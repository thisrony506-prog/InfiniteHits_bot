#!/usr/bin/env python3
"""
Synthesises every sound effect and both music beds of Bubble Blast.

The game ships 9 SFX and 2 music loops under
app/src/main/res/raw/ - all of them are generated here so the repository keeps
no binary blobs that cannot be regenerated or tweaked.

Requirements: numpy, soundfile (pip install numpy soundfile)
Run:          python3 tools/generate_audio.py
"""
import os
import sys

import numpy as np

try:
    import soundfile as sf
except ImportError:  # pragma: no cover
    sys.exit("soundfile is required: pip install --break-system-packages soundfile")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
RATE = 22050


# ----------------------------------------------------------------------
# Building blocks
# ----------------------------------------------------------------------

def t(duration):
    return np.arange(int(RATE * duration)) / RATE


def envelope(signal, attack=0.005, decay=0.05, sustain=0.6, release=0.1):
    """Classic ADSR shape scaled to the length of `signal`."""
    n = len(signal)
    a = max(1, int(attack * RATE))
    d = max(1, int(decay * RATE))
    r = max(1, int(release * RATE))
    s = max(0, n - a - d - r)
    env = np.concatenate([
        np.linspace(0.0, 1.0, a),
        np.linspace(1.0, sustain, d),
        np.full(s, sustain),
        np.linspace(sustain, 0.0, r),
    ])
    if len(env) < n:
        env = np.pad(env, (0, n - len(env)))
    return signal * env[:n]


def exp_decay(signal, tau=0.12):
    return signal * np.exp(-np.arange(len(signal)) / (tau * RATE))


def sine(freq, duration, phase=0.0):
    return np.sin(2 * np.pi * freq * t(duration) + phase)


def triangle(freq, duration):
    x = 2 * np.pi * freq * t(duration)
    return 2.0 / np.pi * np.arcsin(np.sin(x))


def square(freq, duration, duty=0.5):
    x = (freq * t(duration)) % 1.0
    return np.where(x < duty, 1.0, -1.0)


def saw(freq, duration):
    x = (freq * t(duration)) % 1.0
    return 2.0 * x - 1.0


def glide(f0, f1, duration, kind="sine"):
    """Frequency sweep built sample by sample (phase continuous)."""
    times = t(duration)
    freq = np.linspace(f0, f1, len(times))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    if kind == "sine":
        return np.sin(phase)
    if kind == "triangle":
        return 2.0 / np.pi * np.arcsin(np.sin(phase))
    return np.where((phase / (2 * np.pi)) % 1.0 < 0.5, 1.0, -1.0)


def noise(duration, seed=7):
    rng = np.random.default_rng(seed)
    return rng.uniform(-1.0, 1.0, int(RATE * duration))


def lowpass(signal, cutoff):
    """One-pole low pass; good enough to soften square waves."""
    alpha = 1.0 - np.exp(-2 * np.pi * cutoff / RATE)
    out = np.empty_like(signal)
    acc = 0.0
    for i, sample in enumerate(signal):
        acc += alpha * (sample - acc)
        out[i] = acc
    return out


def mix(*layers):
    length = max(len(layer) for layer in layers)
    out = np.zeros(length)
    for layer in layers:
        out[:len(layer)] += layer
    return out


def silence(duration):
    return np.zeros(int(RATE * duration))


def normalize(signal, peak=0.85):
    maximum = float(np.max(np.abs(signal))) or 1.0
    return signal * (peak / maximum)


def fade_edges(signal, fade=0.008):
    n = max(1, int(fade * RATE))
    n = min(n, len(signal) // 2)
    if n <= 0:
        return signal
    signal = signal.copy()
    signal[:n] *= np.linspace(0.0, 1.0, n)
    signal[-n:] *= np.linspace(1.0, 0.0, n)
    return signal


def write(name, signal, peak=0.85):
    os.makedirs(RAW, exist_ok=True)
    data = fade_edges(normalize(signal, peak)).astype(np.float32)
    path = os.path.join(RAW, name + ".ogg")
    sf.write(path, data, RATE, format="OGG", subtype="VORBIS")
    print(f"  {name}.ogg  {len(data) / RATE:5.2f}s  {os.path.getsize(path) / 1024:6.1f} KB")


# ----------------------------------------------------------------------
# Sound effects
# ----------------------------------------------------------------------

def sfx_shoot():
    # A short rubbery "thwip": a fast downward sweep plus a soft click.
    body = glide(880, 260, 0.13, "triangle") * np.exp(-np.arange(int(RATE * 0.13)) / (0.045 * RATE))
    click = noise(0.02, seed=3) * np.exp(-np.arange(int(RATE * 0.02)) / (0.004 * RATE)) * 0.25
    return normalize(mix(body, click), 0.7)


def sfx_pop():
    # Bubble pop = tiny noise transient + a quick upward blip.
    burst = noise(0.05, seed=11) * np.exp(-np.arange(int(RATE * 0.05)) / (0.012 * RATE))
    blip = sine(1200, 0.09) * np.exp(-np.arange(int(RATE * 0.09)) / (0.03 * RATE))
    return normalize(mix(burst * 0.7, blip), 0.8)


def sfx_drop():
    # Falling cluster: descending airy sweep with a wobble.
    fall = glide(700, 150, 0.36, "sine")
    wobble = 1.0 + 0.25 * np.sin(2 * np.pi * 14 * t(0.36))
    air = lowpass(noise(0.36, seed=5), 1200) * 0.3
    return normalize((fall * wobble + air)[: int(RATE * 0.36)] * np.linspace(1.0, 0.0, int(RATE * 0.36)), 0.75)


def sfx_combo():
    # Two rising notes: the classic combo reward.
    first = exp_decay(sine(784, 0.14), 0.06)
    second = exp_decay(sine(1175, 0.22), 0.09)
    return normalize(mix(first, np.concatenate([silence(0.1), second])), 0.8)


def sfx_powerup():
    # Rising major arpeggio, bright and short.
    notes = [523.25, 659.25, 783.99, 1046.5]
    parts = []
    for index, freq in enumerate(notes):
        tone = exp_decay(triangle(freq, 0.2), 0.07)
        parts.append(np.concatenate([silence(0.055 * index), tone]))
    return normalize(mix(*parts), 0.8)


def sfx_star():
    # Sparkle: three high notes with a shimmer tail.
    notes = [1046.5, 1318.5, 1568.0]
    parts = []
    for index, freq in enumerate(notes):
        tone = exp_decay(sine(freq, 0.3), 0.1)
        parts.append(np.concatenate([silence(0.06 * index), tone]))
    shimmer = exp_decay(sine(2093.0, 0.45), 0.16) * 0.35
    return normalize(mix(*parts, np.concatenate([silence(0.18), shimmer])), 0.8)


def sfx_level_complete():
    # Four note fanfare in C major.
    melody = [(523.25, 0.16), (659.25, 0.16), (783.99, 0.16), (1046.5, 0.45)]
    parts = []
    cursor = 0.0
    for freq, length in melody:
        tone = exp_decay(triangle(freq, length), 0.09)
        tone = mix(tone, exp_decay(sine(freq * 2, length), 0.05) * 0.25)
        parts.append(np.concatenate([silence(cursor), tone]))
        cursor += length * 0.85
    return normalize(mix(*parts), 0.85)


def sfx_game_over():
    # Descending minor phrase, softened with a low pad.
    melody = [(523.25, 0.22), (415.30, 0.22), (349.23, 0.3), (261.63, 0.6)]
    parts = []
    cursor = 0.0
    for freq, length in melody:
        tone = exp_decay(lowpass(triangle(freq, length), 2600), 0.12)
        parts.append(np.concatenate([silence(cursor), tone]))
        cursor += length * 0.85
    pad = lowpass(sine(130.81, cursor + 0.6), 800) * 0.3
    return normalize(mix(*parts, pad), 0.8)


def sfx_click():
    tiny = exp_decay(sine(1500, 0.045), 0.012) * 0.6
    body = exp_decay(noise(0.03, seed=17), 0.008) * 0.25
    return normalize(mix(tiny, body), 0.55)


# ----------------------------------------------------------------------
# Music
# ----------------------------------------------------------------------

def note(freq, duration, wave="triangle", gain=0.5, attack=0.01, release=0.06):
    if wave == "triangle":
        raw = triangle(freq, duration)
    elif wave == "square":
        raw = lowpass(square(freq, duration, 0.45), 3000)
    elif wave == "saw":
        raw = lowpass(saw(freq, duration), 2200)
    else:
        raw = sine(freq, duration)
    return envelope(raw, attack=attack, decay=0.04, sustain=0.75, release=release) * gain


def kick(gain=0.55):
    length = 0.16
    body = glide(140, 48, length, "sine") * np.exp(-np.arange(int(RATE * length)) / (0.05 * RATE))
    return body * gain


def hat(gain=0.12, seed=23):
    length = 0.05
    return noise(length, seed=seed) * np.exp(-np.arange(int(RATE * length)) / (0.008 * RATE)) * gain


def place(track, sound, at_seconds):
    start = int(at_seconds * RATE)
    end = start + len(sound)
    if end > len(track):
        sound = sound[: len(track) - start]
        end = len(track)
    track[start:end] += sound
    return track


def make_music(progression, tempo, bars, lead_pattern, bass_pattern, style):
    """
    Builds a seamless loop:
      progression - list of (root_midi, [chord midi notes])
      lead_pattern - per eighth note: chord degree or None
    The loop length is exactly `bars * 4` beats, so it repeats cleanly.
    """
    beat = 60.0 / tempo
    bar_length = beat * 4
    total = bar_length * bars
    track = np.zeros(int(total * RATE))

    def midi_to_freq(m):
        return 440.0 * (2 ** ((m - 69) / 12.0))

    steps_per_bar = 8  # eighth notes
    step = bar_length / steps_per_bar

    for bar in range(bars):
        root, chord = progression[bar % len(progression)]
        bar_start = bar * bar_length

        # pad chord (home only), bass line, percussion
        if style == "home":
            for offset, semitone in ((0, 0), (2, 4), (4, 7)):
                pad = note(midi_to_freq(root + 12 + semitone), bar_length, "sine", 0.16,
                           attack=0.25, release=0.4)
                place(track, pad, bar_start + offset * 0.02)
        for index, degree in enumerate(bass_pattern):
            if degree is None:
                continue
            freq = midi_to_freq(root - 12 + degree)
            place(track, note(freq, step * 0.9, "triangle", 0.30, attack=0.005, release=0.05),
                  bar_start + index * step)

        if style == "home":
            place(track, kick(0.35), bar_start)
            place(track, kick(0.25), bar_start + beat * 2.5)
            place(track, hat(0.07, seed=bar + 31), bar_start + beat * 1.5)
            place(track, hat(0.07, seed=bar + 61), bar_start + beat * 3.5)
        else:
            place(track, kick(0.5), bar_start)
            place(track, kick(0.42), bar_start + beat * 1.5)
            place(track, kick(0.45), bar_start + beat * 2)
            place(track, kick(0.42), bar_start + beat * 3.5)
            for eighth in range(steps_per_bar):
                if eighth % 2 == 1:
                    place(track, hat(0.10, seed=bar * 10 + eighth), bar_start + eighth * step)

        # lead melody
        for index, degree in enumerate(lead_pattern):
            if degree is None:
                continue
            semitone = chord[degree % len(chord)] if degree < len(chord) + 1 else chord[0]
            freq = midi_to_freq(root + 24 + semitone + (12 if degree >= len(chord) + 1 else 0))
            wave = "triangle" if style == "home" else "square"
            gain = 0.22 if style == "home" else 0.20
            place(track, note(freq, step * 1.6 if index % 4 == 0 else step * 0.8,
                              wave, gain, attack=0.008, release=0.1),
                  bar_start + index * step)

    return fade_edges(normalize(track, 0.72), fade=0.01)


def music_home():
    """Warm C - Am - F - G progression, relaxed 96 BPM."""
    progression = [
        (60, [0, 4, 7]),    # C
        (57, [0, 3, 7]),    # Am
        (53, [0, 4, 7]),    # F
        (55, [0, 4, 7]),    # G
    ]
    lead = [0, None, 1, 2, None, 1, 0, None]
    bass = [0, None, 0, None, 2, None, 1, None]
    return make_music(progression, 96, 4, lead, bass, "home")


def music_game():
    """Bouncier C - G - Am - F loop at 124 BPM with a square lead."""
    progression = [
        (60, [0, 4, 7]),    # C
        (55, [0, 4, 7]),    # G
        (57, [0, 3, 7]),    # Am
        (53, [0, 4, 7]),    # F
    ]
    lead = [0, 2, 1, None, 2, 3, 1, None]
    bass = [0, None, 0, None, 1, None, 2, None]
    return make_music(progression, 124, 4, lead, bass, "game")


def main():
    print("sound effects:")
    write("sfx_shoot", sfx_shoot())
    write("sfx_pop", sfx_pop())
    write("sfx_drop", sfx_drop())
    write("sfx_combo", sfx_combo())
    write("sfx_powerup", sfx_powerup())
    write("sfx_star", sfx_star())
    write("sfx_level_complete", sfx_level_complete())
    write("sfx_game_over", sfx_game_over())
    write("sfx_click", sfx_click())

    print("music:")
    write("music_home", music_home(), peak=0.7)
    write("music_game", music_game(), peak=0.7)


if __name__ == "__main__":
    main()
