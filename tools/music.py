"""The mod's music and sound effects: what gen() writes, and the sounds.json for them. The tracks and effects
themselves are composed in compose.py and played by FluidSynth with the FluidR3 GM soundfont; the instruments below
(the first, synthesised score) are kept for the effects' sub-bass layers and for reference.

The original description of the synthesised score follows:
The mod's own music and sound effects, composed and synthesised here: unhurried tracks that take their time to
build - a pad, then a harp's arpeggio, then a melody on a lyre over soft brushed drums, then back down - in the
modes of each realm, and the sounds of the shrine and the gate.

Everything is original: instruments are made from sine partials (a plucked string's harmonics dying at their own
rates, a bell's inharmonic partials, a pad of slowly beating voices), mixed, warmed, and put in a hall with a
convolution reverb. Written as mono Ogg Vorbis with ffmpeg.
"""
import os
import random
import subprocess
import tempfile
import wave

import numpy as np

SR = 32000


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def env(n, attack, release, total=None):
    """An envelope n samples long: a rise, a hold, a fall (in seconds)."""
    e = np.ones(n)
    a, r = int(attack * SR), int(release * SR)
    if a: e[:a] = np.linspace(0, 1, a) ** 1.5
    if r: e[-r:] *= np.linspace(1, 0, r) ** 2
    return e


def pluck(freq, dur, bright=1.0, seed=0):
    """A plucked string: harmonics that die away faster the higher they are, a little inharmonic, a soft attack."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    rng = np.random.default_rng(seed)
    for k in range(1, 9):
        f = freq * k * (1 + 0.0007 * k * k)
        if f > SR / 2.2: break
        amp = (bright ** (k - 1)) / k ** 1.2
        decay = 1.6 + 2.2 * k
        out += amp * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-decay * t)
    out *= env(n, 0.004, min(0.25, dur * 0.4))
    return out


def bell(freq, dur):
    n = int(dur * SR)
    t = np.arange(n) / SR
    parts = [(1.0, 1.0, 1.4), (2.76, 0.45, 2.6), (5.4, 0.22, 4.0), (8.93, 0.1, 6.0)]
    out = sum(a * np.sin(2 * np.pi * freq * r * t) * np.exp(-d * t) for r, a, d in parts)
    return out * env(n, 0.002, min(0.4, dur * 0.4))


def pad(freqs, dur, warmth=0.5):
    """A pad: each note three slightly detuned voices with a soft second harmonic, rising and falling slowly."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for f in freqs:
        for det in (-0.0035, 0.0, 0.004):
            ff = f * (1 + det)
            out += np.sin(2 * np.pi * ff * t) + warmth * 0.35 * np.sin(4 * np.pi * ff * t + 0.5)
    out *= (0.85 + 0.15 * np.sin(2 * np.pi * 0.23 * t)) / (len(freqs) * 3)
    return out * env(n, min(1.6, dur * 0.35), min(2.0, dur * 0.4))


def bass(freq, dur):
    n = int(dur * SR)
    t = np.arange(n) / SR
    out = np.sin(2 * np.pi * freq * t) + 0.25 * np.sin(4 * np.pi * freq * t)
    return out * env(n, 0.02, min(0.6, dur * 0.5)) * np.exp(-0.35 * t)


def kick(dur=0.35):
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = 52 + 70 * np.exp(-28 * t)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-9 * t)


def brush(dur=0.18, seed=0):
    rng = np.random.default_rng(seed)
    n = int(dur * SR)
    x = rng.normal(0, 1, n)
    x = np.convolve(x, np.ones(6) / 6, mode="same") - np.convolve(x, np.ones(40) / 40, mode="same")
    return x * np.exp(-np.arange(n) / SR * 22) * 0.6


def reverb(x, seconds=2.6, wet=0.32, seed=1):
    """A hall: the signal convolved with a decaying, darkening burst of noise."""
    rng = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = rng.normal(0, 1, n) * np.exp(-3.2 * t / seconds * 2.2)
    ir = np.convolve(ir, np.ones(8) / 8, mode="same")
    ir[0] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:len(x)]
    return x * (1 - wet) + y * wet * 1.8


def compress(x, threshold=0.35, ratio=3.0, window=0.25):
    """Evens the level: where the music is loud (its running RMS over the threshold) it is turned down, gently."""
    w = int(window * SR)
    rms = np.sqrt(np.convolve(x ** 2, np.ones(w) / w, mode="same") + 1e-9)
    peak = np.max(rms)
    level = rms / peak
    gain = np.where(level > threshold, (threshold / level) ** (1 - 1 / ratio), 1.0)
    gain = np.convolve(gain, np.ones(w) / w, mode="same")
    return x * gain


def warm(x):
    x = np.tanh(x * 1.2) / np.tanh(1.2)
    return np.convolve(x, np.array([0.25, 0.5, 0.25]), mode="same")


def crackle(n, seed=5, level=0.012):
    rng = np.random.default_rng(seed)
    out = rng.normal(0, 0.0015, n)
    for i in rng.integers(0, n, n // 2400):
        out[i:i + 30] += rng.normal(0, level, len(out[i:i + 30]))
    return out


def place(mix, sig, start, gain=1.0):
    s = int(start * SR)
    if s >= len(mix): return
    end = min(len(mix), s + len(sig))
    mix[s:end] += sig[:end - s] * gain


def write_ogg(x, path, quality=4):
    x = x / max(1e-6, np.max(np.abs(x))) * 0.89
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        name = tmp.name
    with wave.open(name, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    os.makedirs(os.path.dirname(path), exist_ok=True)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", name, "-c:a", "libvorbis", "-q:a", str(quality), path], check=True)
    os.remove(name)


# ------------------------------------------------------------------------------------------------- the tracks
SCALES = {"dorian": [0, 2, 3, 5, 7, 9, 10], "lydian": [0, 2, 4, 6, 7, 9, 11], "aeolian": [0, 2, 3, 5, 7, 8, 10], "phrygian": [0, 1, 3, 5, 7, 8, 10]}


def track(root, mode, bpm, chords, seed, length_bars=32, lofi=True, dark=False):
    """One track. chords: (root offset in semitones, chord tones as offsets) per two bars, cycled."""
    rng = random.Random(seed)
    beat = 60.0 / bpm
    bar = beat * 4
    total = length_bars * bar + 4.0
    mix = np.zeros(int(total * SR))
    scale = SCALES[mode]

    def degree(d, octave):
        return root + 12 * octave + scale[d % 7] + 12 * (d // 7)

    sections = {"intro": range(0, 4), "a": range(4, 12), "b": range(12, 20), "a2": range(20, 28), "outro": range(28, 32)}
    melody_bars = set(sections["b"]) | set(sections["a2"])
    arp_bars = set(range(4, 30))
    drum_bars = set(range(12, 28))
    # a melody: two four-bar phrases, the second answering the first, made of steps and small leaps
    phrase = []
    d = rng.choice([4, 2, 0])
    rhythms = [[1.5, 0.5, 1, 1], [1, 1, 2], [0.5, 0.5, 1, 2], [2, 1, 1], [1, 0.5, 0.5, 2], [3, 1]]
    for b in range(8):
        pattern = rng.choice(rhythms)
        notes = []
        for dur in pattern:
            d += rng.choice([-2, -1, -1, 0, 1, 1, 2, 3]) if rng.random() < 0.85 else rng.choice([-4, 4])
            d = max(-1, min(9, d))
            notes.append((d, dur))
        phrase.append(notes)
    phrase[3][-1] = (2, phrase[3][-1][1])   # the first phrase rests on the third
    phrase[7][-1] = (0, phrase[7][-1][1])   # the answer comes home
    for b in range(length_bars):
        t0 = b * bar + 1.0
        ci = (b // 2) % len(chords)
        croot, tones = chords[ci]
        chord_midis = [root + croot + x for x in tones]
        if b % 2 == 0:
            place(mix, pad([midi(m) for m in chord_midis], bar * 2 + 1.2), t0, 0.55 if not dark else 0.65)
            place(mix, bass(midi(root + croot - 12), bar * 2), t0, 0.28)
        if b in arp_bars:
            # an arpeggio on a harp: up through the chord and back, eighths, a little loose in time
            seq = chord_midis + [chord_midis[1] + 12, chord_midis[2] + 12] if len(chord_midis) >= 3 else chord_midis
            order = seq + seq[-2:0:-1]
            for i in range(8):
                m = order[i % len(order)] + 12
                when = t0 + i * beat / 2 + rng.uniform(-0.008, 0.008)
                place(mix, pluck(midi(m), beat * 1.6, bright=0.55, seed=i + b * 8), when, 0.16 if b < 8 else 0.13)
        if b in melody_bars:
            notes = phrase[(b - 12) % 8]
            t = t0
            for dd, dur in notes:
                m = degree(dd, 5 if not dark else 4)
                place(mix, pluck(midi(m), dur * beat * 1.4, bright=0.7, seed=b * 13 + dd), t + rng.uniform(-0.01, 0.01), 0.3)
                if rng.random() < 0.18:
                    place(mix, bell(midi(m + 12), 2.0), t, 0.07)
                t += dur * beat
        if b in drum_bars:
            for i in range(4):
                if i in (0, 2) and not (dark and i == 2): place(mix, kick(), t0 + i * beat, 0.32)
                place(mix, brush(seed=b * 4 + i), t0 + i * beat + beat / 2, 0.12)
                if i in (1, 3): place(mix, brush(0.28, seed=b * 9 + i), t0 + i * beat, 0.18)
        if b in (3, 11, 19, 27):   # a bell to mark each turn of the music
            place(mix, bell(midi(degree(4, 5)), 3.0), t0 + bar * 0.75, 0.12)
    # the end: the last chord held, a bell, and quiet
    out = compress(reverb(mix, 3.0, 0.34, seed))
    if lofi:
        out = warm(out) + crackle(len(out), seed)
    fade = int(3.0 * SR)
    out[-fade:] *= np.linspace(1, 0, fade)
    out[:int(0.6 * SR)] *= np.linspace(0, 1, int(0.6 * SR))
    return out


def M(*xs):
    return list(xs)


TRACKS = {
    # the hub: warm and patient, D dorian - Dm9, G13, Fmaj7, Cadd9
    "hub": dict(root=50, mode="dorian", bpm=76, seed=11,
                chords=[(0, M(0, 3, 7, 10, 14)), (5, M(0, 4, 7, 10, 16)), (3, M(0, 4, 7, 11)), (-2, M(0, 4, 7, 14))]),
    # Olympus: bright and open, G lydian - Gmaj9, A/G, Em9, Cmaj7#11
    "olympus": dict(root=55, mode="lydian", bpm=82, seed=21,
                    chords=[(0, M(0, 4, 7, 11, 14)), (2, M(0, 4, 7, 12)), (-3, M(0, 3, 7, 10, 14)), (-7, M(0, 4, 7, 11, 18))]),
    # the abyss: slow and deep, E aeolian - Em9, Cmaj7, Am9, Bsus4
    "abyss": dict(root=52, mode="aeolian", bpm=64, seed=31,
                  chords=[(0, M(0, 3, 7, 10, 14)), (-4, M(0, 4, 7, 11)), (5, M(0, 3, 7, 10, 14)), (7, M(0, 5, 7, 10))]),
    # the underworld: dark and sparse, C phrygian - Cm(add9), Dbmaj7, Fm9, Gsus4
    "underworld": dict(root=48, mode="phrygian", bpm=60, seed=41, dark=True,
                       chords=[(0, M(0, 3, 7, 14)), (1, M(0, 4, 7, 11)), (5, M(0, 3, 7, 10, 14)), (7, M(0, 5, 7))]),
}


# ------------------------------------------------------------------------------------------------- effects
def sfx():
    out = {}

    def finish(x, wet=0.3, secs=1.6):
        y = reverb(x, secs, wet, 3)
        return y

    n = lambda s: np.zeros(int(s * SR))
    # the orb makes something: a rising run of bells and a shimmer
    x = n(1.8)
    for i, m in enumerate((74, 78, 81, 86)):
        place(x, bell(midi(m), 1.2), i * 0.09, 0.5)
    place(x, brush(0.6, seed=2) * np.linspace(1, 0, int(0.6 * SR)), 0.3, 0.2)
    out["orb_make"] = finish(x)
    # a core set in the orb: a swell that rises into a chord
    x = n(2.2)
    t = np.arange(len(x)) / SR
    swell = np.sin(2 * np.pi * (110 + 110 * t / 2.2) * t) * np.sin(np.pi * t / 2.2) ** 2
    x += swell * 0.5
    place(x, pad([midi(62), midi(69), midi(74)], 1.6), 0.6, 0.8)
    out["orb_core"] = finish(x, 0.35, 2.2)
    # a pattern shown to the orb: a glassy shimmer
    x = n(1.2)
    for m in (86, 90, 93):
        place(x, bell(midi(m), 1.0), 0.0 + (m - 86) * 0.01, 0.35)
    out["orb_pattern"] = finish(x)
    # ichor poured in: little bubbles, falling in pitch
    x = n(0.9)
    for i in range(7):
        f = 900 - i * 70
        b = np.sin(2 * np.pi * f * np.arange(int(0.07 * SR)) / SR * (1 + np.arange(int(0.07 * SR)) / SR * 6))
        place(x, b * np.exp(-np.arange(len(b)) / SR * 40), i * 0.09, 0.45)
    out["orb_ichor"] = finish(x, 0.2, 0.8)
    # the shrine wakes: a chord blooming from low to high
    x = n(3.4)
    place(x, pad([midi(50), midi(57), midi(62), midi(66)], 3.0), 0.0, 1.0)
    for i, m in enumerate((62, 66, 69, 74, 78)):
        place(x, pluck(midi(m), 1.5, 0.6, seed=i), 0.4 + i * 0.12, 0.35)
    out["shrine_awaken"] = finish(x, 0.4, 2.6)
    # a keystone set: a deep bell and its ring
    x = n(2.6)
    place(x, bell(midi(45), 2.4), 0, 0.8)
    place(x, bell(midi(57), 2.0), 0.02, 0.4)
    out["keystone_set"] = finish(x, 0.35, 2.4)
    # the gate wakes: a great chord over a rumble
    x = n(5.0)
    t = np.arange(len(x)) / SR
    rumble = np.convolve(np.random.default_rng(9).normal(0, 1, len(x)), np.ones(200) / 200, mode="same") * np.sin(np.pi * t / 5.0)
    x += rumble * 2.2
    place(x, pad([midi(38), midi(45), midi(50), midi(57), midi(62), midi(66)], 4.6), 0.2, 1.3)
    for i, m in enumerate((74, 78, 81, 86)):
        place(x, bell(midi(m), 3.0), 0.8 + i * 0.25, 0.3)
    out["gate_awaken"] = finish(x, 0.42, 3.0)
    # a soft tick for the screens
    x = n(0.12)
    tick = np.sin(2 * np.pi * 1800 * np.arange(int(0.05 * SR)) / SR) * np.exp(-np.arange(int(0.05 * SR)) / SR * 120)
    place(x, tick, 0, 0.6)
    out["ui_click"] = x
    return out


# every track, in order: the hub's is composed here, the rest in score.py
TRACK_IDS = ["hub", "hermitage", "olympus", "olympus_peak", "abyss", "abyss_trench", "underworld", "tartarus", "passage",
             "arena", "trial", "summit", "wrath", "titan", "oracle", "citadel", "citadel_sea", "citadel_shade", "gate"]
EFFECT_IDS = ["orb_make", "orb_core", "orb_pattern", "orb_ichor", "shrine_awaken", "keystone_set", "gate_awaken", "ui_click"]
# bump to write every track and effect again
SCORE_VERSION = 6


def render(name):
    """A track, composed around its harmony and played by real instruments (harmony.py, through FluidSynth and the
    FluidR3 GM soundfont) - the Ruined Gate's the synthesised one - all brought to the same loudness."""
    import harmony
    x = harmony.TRACKS[name]()
    rms = np.sqrt(np.mean(x ** 2)) + 1e-9
    x = x * (0.17 / rms)
    return np.tanh(x * 1.1) / np.tanh(1.1)


def gen(assets, wj, only_missing=True):
    sounds = {}
    base = os.path.join(assets, "sounds")
    stamp = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".score_version")
    fresh = not os.path.exists(stamp) or open(stamp).read().strip() != str(SCORE_VERSION)
    for name in TRACK_IDS:
        path = os.path.join(base, "music", f"{name}.ogg")
        if fresh or not (only_missing and os.path.exists(path)):
            write_ogg(render(name), path, quality=3)
        sounds[f"music.{name}"] = {"sounds": [{"name": f"realms_of_chaos:music/{name}", "stream": True}]}
    effects = None
    for name in EFFECT_IDS:
        path = os.path.join(base, "fx", f"{name}.ogg")
        # (the effects are kept as they are once made: only a missing one is made again)
        if not os.path.exists(path):
            if effects is None:
                import compose
                effects = compose.effects()
            write_ogg(effects[name], path, quality=5)
        sounds[name] = {"sounds": [f"realms_of_chaos:fx/{name}"], "subtitle": f"subtitles.realms_of_chaos.{name}"}
    # and the second set of effects - the relics, a fight, the blocks, idle machines (sfx_more.py) - made once
    import sfx_more
    more = None
    for name in sfx_more.SUBTITLES:
        path = os.path.join(base, "fx", f"{name}.ogg")
        if not os.path.exists(path):
            more = more or sfx_more.effects()
            write_ogg(more[name], path, quality=5)
        sounds[name] = {"sounds": [f"realms_of_chaos:fx/{name}"], "subtitle": f"subtitles.realms_of_chaos.{name}"}
    wj(os.path.join(assets, "sounds.json"), sounds)
    with open(stamp, "w") as f:
        f.write(str(SCORE_VERSION))
    return list(sounds)


def lengths(assets):
    """Each track's length in seconds, read back from its file: what a jukebox needs to know."""
    out = {}
    for name in TRACK_IDS:
        path = os.path.join(assets, "sounds", "music", f"{name}.ogg")
        r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True)
        out[name] = float(r.stdout.strip() or 0)
    return out


def _more_subtitles():
    import sfx_more
    return sfx_more.SUBTITLES


SUBTITLES = {"orb_make": "The Orb of Plenty makes something", "orb_core": "A core is set in the orb", "orb_pattern": "The orb takes a pattern",
             "orb_ichor": "Ichor is poured", "shrine_awaken": "A shrine wakes", "keystone_set": "A keystone is set", "gate_awaken": "The gate wakes",
             "ui_click": "Click"}
SUBTITLES.update(_more_subtitles())
