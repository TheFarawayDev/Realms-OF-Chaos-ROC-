"""The rest of the mod's score, and its sound effects. Each track has its own key and register, tempo, metre, rhythm
and instruments, so no two sound alike:

  hub         D dorian, middle    76 bpm  4/4  lyre, harp and brushes, unhurried          (music.py)
  hermitage   F major pent., high 66 bpm  4/4  a music box and a kalimba, no drums, rubato
  olympus     G lydian, high     112 bpm  3/4  a waltz: flute over pizzicato, celesta, horns
  abyss       B aeolian, low      54      6/8  rolling marimba, choir "oo", whale song, drips
  underworld  C# phrygian, lowest 46 bpm  5/4  taiko, organ, low choir and cello, a tolling bell
  passage     A hijaz, middle    200 (8ths) 7/8  oud ostinato, frame drum, ney flute, ticking
  trial       E minor, low-mid   138 bpm  4/4  driving strings, taiko and snare, brass, choir
  citadel     Bb mixolydian, mid  84 bpm  4/4  a march: horns, snare drum, timpani, low strings
  gate        Ab lydian, highest  40 bpm  free choir and glass harmonics, no beat at all

All synthesised from sine partials and noise with numpy: nothing sampled.
"""
import math
import random

import numpy as np

from music import SR, midi, env, pluck, bell, pad, bass, kick, brush, reverb, compress, warm, place

TAU = 2 * np.pi


# ------------------------------------------------------------------------------------------------ helpers
def tvec(dur):
    return np.arange(int(dur * SR)) / SR


def lowpass(x, cutoff):
    """A gentle low-pass by FFT: a smooth roll-off above the cutoff."""
    n = len(x)
    if n < 16: return x
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= 1 / (1 + (f / cutoff) ** 4)
    return np.fft.irfft(spec, n)


def bandpass(x, lo, hi):
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= (1 / (1 + (lo / np.maximum(f, 1)) ** 4)) * (1 / (1 + (f / hi) ** 4))
    return np.fft.irfft(spec, n)


def noise(dur, seed=0):
    return np.random.default_rng(seed).normal(0, 1, int(dur * SR))


def vibrato(t, rate=5.2, depth=0.004, delay=0.25):
    ramp = np.clip((t - delay) / 0.4, 0, 1)
    return 1 + depth * ramp * np.sin(TAU * rate * t)


def osc(freq, t, fm=None):
    """A sine whose frequency may wander: integrated phase."""
    if fm is None:
        return np.sin(TAU * freq * t)
    return np.sin(TAU * np.cumsum(freq * fm) / SR)


# ------------------------------------------------------------------------------------------------ instruments
def strings(freqs, dur, attack=0.35, bright=1.0, voices=3, seed=0):
    """A section of strings: band-limited saws, slightly detuned, with vibrato and a slow bow."""
    t = tvec(dur)
    out = np.zeros(len(t))
    rng = np.random.default_rng(seed)
    for f in freqs:
        for v in range(voices):
            det = 1 + (v - (voices - 1) / 2) * 0.0032
            vib = vibrato(t, 5.0 + rng.uniform(-0.4, 0.4), 0.0035)
            ph = TAU * np.cumsum(f * det * vib) / SR
            top = int(min(18, 5200 * bright / f))
            for k in range(1, max(2, top)):
                out += np.sin(k * ph + rng.uniform(0, 6)) / k ** 1.15
    out /= max(1, len(freqs) * voices) * 2.2
    return out * env(len(t), min(attack, dur * 0.4), min(0.5, dur * 0.35))


def brass(freq, dur, attack=0.06, bright=1.0, seed=0):
    """A horn: a saw whose upper harmonics open up after the attack, with a little scoop into the note."""
    t = tvec(dur)
    scoop = 1 - 0.012 * np.exp(-t * 18)
    ph = TAU * np.cumsum(freq * scoop * vibrato(t, 5.0, 0.003, 0.35)) / SR
    out = np.zeros(len(t))
    top = int(min(20, 4200 * bright / freq))
    for k in range(1, max(2, top)):
        open_ = np.clip(t / (0.02 + 0.018 * k), 0, 1)
        out += np.sin(k * ph) / k ** 1.05 * open_ * (0.92 ** k)
    return out * 0.45 * env(len(t), attack, min(0.25, dur * 0.3))


VOWELS = {"a": [(800, 1.0, 90), (1150, 0.5, 100), (2900, 0.15, 140)], "o": [(450, 1.0, 70), (800, 0.45, 90), (2830, 0.08, 140)],
          "u": [(325, 1.0, 60), (700, 0.25, 80), (2700, 0.05, 140)], "e": [(400, 1.0, 70), (2000, 0.4, 120), (2550, 0.25, 140)]}


def choir(freqs, dur, vowel="a", attack=0.6, voices=4, seed=0):
    """A choir: for each note, voices whose harmonics are shaped by the formants of a vowel."""
    t = tvec(dur)
    out = np.zeros(len(t))
    rng = np.random.default_rng(seed)
    forms = VOWELS[vowel]
    for f in freqs:
        for v in range(voices):
            det = 1 + rng.uniform(-0.006, 0.006)
            ph = TAU * np.cumsum(f * det * vibrato(t, 5.4 + rng.uniform(-0.5, 0.5), 0.006, 0.3)) / SR
            k = 1
            while f * k < 4200 and k < 40:
                fk = f * k
                a = sum(g * math.exp(-((fk - c) / w) ** 2 / 2) for c, g, w in forms) + 0.02 / k
                out += a * np.sin(k * ph + rng.uniform(0, 6))
                k += 1
    out /= max(1, len(freqs) * voices) * 0.9
    breath = lowpass(noise(dur, seed), 3000) * 0.004
    return (out + breath) * env(len(t), min(attack, dur * 0.45), min(0.8, dur * 0.4))


def flute(freq, dur, breath=0.05, seed=0, ney=False):
    t = tvec(dur)
    vib = vibrato(t, 5.0, 0.006 if not ney else 0.009, 0.2)
    ph = TAU * np.cumsum(freq * vib) / SR
    amps = [1.0, 0.32, 0.1, 0.04] if not ney else [1.0, 0.5, 0.25, 0.14, 0.08]
    out = sum(a * np.sin((k + 1) * ph) for k, a in enumerate(amps))
    air = bandpass(noise(dur, seed), freq * 0.8, freq * 3.5) * (breath * (3 if ney else 1))
    return (out + air) * 0.5 * env(len(t), 0.06 if not ney else 0.12, min(0.15, dur * 0.3))


def marimba(freq, dur=1.2, soft=False):
    t = tvec(dur)
    d = 3.2 if not soft else 2.2
    out = np.sin(TAU * freq * t) * np.exp(-d * t) + 0.25 * np.sin(TAU * freq * 4 * t) * np.exp(-d * 5 * t) \
        + 0.06 * np.sin(TAU * freq * 9.9 * t) * np.exp(-d * 12 * t)
    return out * env(len(t), 0.003, 0.05)


def musicbox(freq, dur=2.0):
    t = tvec(dur)
    parts = [(1.0, 1.0, 1.6), (2.0, 0.28, 3.0), (3.01, 0.1, 4.5), (5.43, 0.05, 7.0)]
    out = sum(a * np.sin(TAU * freq * r * t) * np.exp(-d * t) for r, a, d in parts)
    click = noise(0.004) * 0.05
    out[:len(click)] += click
    return out * env(len(t), 0.001, 0.1)


def kalimba(freq, dur=1.5):
    t = tvec(dur)
    out = np.sin(TAU * freq * t) * np.exp(-2.8 * t) + 0.3 * np.sin(TAU * freq * 5.95 * t) * np.exp(-18 * t)
    return out * env(len(t), 0.002, 0.08)


def pizz(freq, dur=0.5, seed=0):
    return pluck(freq, dur, bright=0.45, seed=seed) * np.exp(-np.arange(int(dur * SR)) / SR * 4)


def oud(freq, dur=0.7, seed=0):
    """A fretted lute with a dark tone and a little bend into the note."""
    t = tvec(dur)
    bend = 1 - 0.01 * np.exp(-t * 30)
    ph = TAU * np.cumsum(freq * bend) / SR
    rng = np.random.default_rng(seed)
    out = sum((0.75 ** (k - 1)) / k * np.sin(k * ph + rng.uniform(0, 6)) * np.exp(-(2.0 + 1.6 * k) * t) for k in range(1, 8))
    return out * env(len(t), 0.003, 0.12)


def organ(freqs, dur, attack=0.08):
    t = tvec(dur)
    trem = 1 + 0.05 * np.sin(TAU * 5.8 * t)
    out = np.zeros(len(t))
    for f in freqs:
        for r, a in ((0.5, 0.6), (1, 1.0), (2, 0.5), (3, 0.25), (4, 0.18)):
            out += a * np.sin(TAU * f * r * t)
    return out * trem / (len(freqs) * 2.5) * env(len(t), attack, min(0.4, dur * 0.3))


def glass(freq, dur, attack=0.5):
    t = tvec(dur)
    out = np.sin(TAU * freq * t) + np.sin(TAU * freq * 1.0035 * t) + 0.3 * np.sin(TAU * freq * 2.001 * t)
    return out * 0.33 * env(len(t), min(attack, dur * 0.45), min(1.0, dur * 0.45))


def sub(freq, dur, attack=0.3):
    t = tvec(dur)
    return np.sin(TAU * freq * t) * env(len(t), min(attack, dur * 0.4), min(0.6, dur * 0.4))


def taiko(pitch=1.0, seed=0, dur=1.1):
    t = tvec(dur)
    f = (58 + 60 * np.exp(-t * 14)) * pitch
    body = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t * 4.2)
    skin = lowpass(noise(dur, seed), 900) * np.exp(-t * 22) * 0.6
    return (body + skin) * env(len(t), 0.001, 0.2)


def timpani(freq, dur=1.6, seed=0):
    t = tvec(dur)
    out = sum(a * np.sin(TAU * freq * r * t) * np.exp(-d * t) for r, a, d in ((1, 1, 2.2), (1.5, 0.4, 3), (1.99, 0.25, 4), (2.44, 0.12, 5)))
    return (out + lowpass(noise(dur, seed), 600) * np.exp(-t * 30) * 0.4) * env(len(t), 0.002, 0.3)


def snare(seed=0, dur=0.3, soft=1.0):
    t = tvec(dur)
    tone = np.sin(TAU * 190 * t) * np.exp(-t * 30)
    rattle = bandpass(noise(dur, seed), 1500, 7000) * np.exp(-t * 16)
    return (0.5 * tone + rattle * 0.8) * soft


def doum(seed=0):
    t = tvec(0.6)
    f = 85 + 40 * np.exp(-t * 20)
    return np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t * 6) + lowpass(noise(0.6, seed), 500) * np.exp(-t * 40) * 0.3


def tek(seed=0):
    t = tvec(0.12)
    return bandpass(noise(0.12, seed), 2000, 8000) * np.exp(-t * 60) + np.sin(TAU * 900 * t) * np.exp(-t * 70) * 0.3


def shaker(seed=0):
    t = tvec(0.09)
    return bandpass(noise(0.09, seed), 5000, 12000) * np.sin(np.pi * np.clip(t / 0.09, 0, 1)) * 0.5


def tick(seed=0):
    t = tvec(0.05)
    return np.sin(TAU * 2600 * t) * np.exp(-t * 160) * 0.5 + bandpass(noise(0.05, seed), 3000, 9000) * np.exp(-t * 200) * 0.3


def triangle_hit(dur=2.0):
    t = tvec(dur)
    return sum(a * np.sin(TAU * f * t) * np.exp(-d * t) for f, a, d in ((3150, 1, 2.0), (4630, 0.5, 2.6), (7400, 0.25, 3.2))) * 0.4


def gong(freq=70, dur=6.0, seed=0):
    t = tvec(dur)
    rng = np.random.default_rng(seed)
    out = np.zeros(len(t))
    for r in (1.0, 1.52, 2.03, 2.74, 3.3, 4.1, 5.2, 6.4):
        swell = 1 - np.exp(-t * (1.2 + r))
        out += (1 / r) * np.sin(TAU * freq * r * (1 + rng.uniform(-0.01, 0.01)) * t) * swell * np.exp(-t * (0.5 + 0.15 * r))
    hit = lowpass(noise(dur, seed), 1500) * np.exp(-t * 18) * 0.5
    return (out + hit) * env(len(t), 0.002, 1.5)


def drip(seed=0):
    t = tvec(0.25)
    f = 1400 * np.exp(-t * 6) + 500
    return np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t * 22) * 0.4


def whale(f0, f1, dur, seed=0):
    t = tvec(dur)
    glide = f0 * (f1 / f0) ** (np.sin(np.pi * t / dur / 2) ** 2)
    ph = TAU * np.cumsum(glide) / SR
    out = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.1 * np.sin(3 * ph)
    return out * env(len(t), dur * 0.3, dur * 0.4) * 0.5


def riser(dur, f0=200, f1=4000, seed=0):
    """Noise swept upward through a narrowing band, swelling: the breath before something arrives."""
    t = tvec(dur)
    x = noise(dur, seed)
    out = np.zeros(len(t))
    steps = 24
    seg = len(t) // steps
    for i in range(steps):
        a, b = i * seg, (i + 1) * seg if i < steps - 1 else len(t)
        fc = f0 * (f1 / f0) ** (i / steps)
        out[a:b] = bandpass(x[a:b] if b - a > 64 else np.zeros(b - a), fc * 0.7, fc * 1.4)
    return out * (t / dur) ** 2.2


def boom(dur=3.0, f0=70, f1=28):
    t = tvec(dur)
    f = f1 + (f0 - f1) * np.exp(-t * 3)
    return np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t * 1.2) * env(len(t), 0.004, 0.8)


# ------------------------------------------------------------------------------------------------ a timeline
class Song:
    def __init__(self, bpm, per_bar, bars, lead=1.0, tail=4.5):
        self.beat = 60.0 / bpm
        self.per_bar = per_bar
        self.bar = self.beat * per_bar
        self.bars = bars
        self.lead = lead
        self.mix = np.zeros(int((lead + bars * self.bar + tail) * SR))

    def at(self, bar, beat=0.0):
        return self.lead + bar * self.bar + beat * self.beat

    def add(self, sig, when, gain=1.0):
        place(self.mix, sig, when, gain)

    def finish(self, room=3.0, wet=0.32, seed=1, lofi=False, fade=3.5):
        out = compress(reverb(self.mix, room, wet, seed))
        if lofi: out = warm(out)
        f = int(fade * SR)
        out[-f:] *= np.linspace(1, 0, f)
        out[:int(0.4 * SR)] *= np.linspace(0, 1, int(0.4 * SR))
        return out


def scale_note(root, scale, d, octave=0):
    return root + 12 * octave + scale[d % len(scale)] + 12 * (d // len(scale))


def melody(rng, bars, rhythms, lo, hi, start, steps=(-2, -1, -1, 1, 1, 2), leap=0.1):
    """A line of scale degrees over some bars: steps mostly, now and then a leap, kept within a range."""
    out = []
    d = start
    for _ in range(bars):
        notes = []
        for dur in rng.choice(rhythms):
            if rng.random() < leap:
                d += rng.choice([-4, -3, 3, 4])
            else:
                d += rng.choice(steps)
            d = max(lo, min(hi, d))
            notes.append((d, dur))
        out.append(notes)
    return out


# ------------------------------------------------------------------------------------------------ the tracks
PENT = [0, 2, 4, 7, 9]
LYDIAN = [0, 2, 4, 6, 7, 9, 11]
AEOLIAN = [0, 2, 3, 5, 7, 8, 10]
PHRYGIAN = [0, 1, 3, 5, 7, 8, 10]
HIJAZ = [0, 1, 4, 5, 7, 8, 10]
MINOR = [0, 2, 3, 5, 7, 8, 11]
MIXO = [0, 2, 4, 5, 7, 9, 10]


def hermitage():
    """A quiet island: a music box turning over a motif, a kalimba answering, a pad of strings. No drums; the time loose."""
    rng = random.Random(51)
    s = Song(66, 4, 40)
    root = 65                                              # F4: the music box plays an octave up
    chords = [(0, [0, 4, 7, 14]), (-3, [0, 3, 7, 10]), (-7, [0, 4, 7, 11]), (-5, [0, 4, 7, 9])]   # F, Dm7, Bbmaj7, C6
    motif = [(4, 1), (2, 0.5), (1, 0.5), (0, 1), (2, 1), (3, 1.5), (2, 0.5), (1, 2)]
    for b in range(40):
        croot, tones = chords[(b // 2) % 4]
        if b % 2 == 0:
            s.add(strings([midi(root - 12 + croot + x) for x in tones], s.bar * 2 + 1.0, attack=1.2, bright=0.5, voices=2, seed=b), s.at(b), 0.32)
            s.add(sub(midi(root - 24 + croot), s.bar * 2, 0.5), s.at(b), 0.25)
        if 4 <= b < 36:
            # the music box: the motif, two bars long, each time a little changed
            if b % 2 == 0:
                t = 0.0
                shift = [0, 0, 1, -1, 2, 0, -2, 0][(b // 2) % 8]
                for d, dur in motif:
                    when = s.at(b, t) + rng.uniform(-0.03, 0.03)
                    s.add(musicbox(midi(scale_note(root + 12, PENT, d + shift))), when, 0.32)
                    t += dur
        if 12 <= b < 32 and b % 2 == 1:
            # the kalimba answers, lower, in the bar the music box rests in
            for i, d in enumerate(rng.sample([0, 1, 2, 3, 4, 5], 4)):
                s.add(kalimba(midi(scale_note(root, PENT, d))), s.at(b, i * 0.75 + 1) + rng.uniform(-0.02, 0.02), 0.28)
        if b in (8, 20, 32):
            s.add(glass(midi(root + 24 + 7), 5.0, 1.5), s.at(b), 0.12)
    return s.finish(3.6, 0.38, 51)


def olympus():
    """A waltz on the mountain: pizzicato oom-pah-pah, a flute high over it, celesta, and the horns at the top."""
    rng = random.Random(61)
    s = Song(112, 3, 72)
    root = 55                                               # G3
    prog = [(0, [0, 4, 7, 11]), (2, [0, 4, 7]), (-3, [0, 3, 7, 10]), (-7, [0, 4, 7, 11, 18]), (0, [0, 4, 7, 14]), (2, [0, 4, 7, 10]), (-5, [0, 4, 7, 12]), (2, [0, 5, 7])]
    rhythms = [[2, 1], [1.5, 0.5, 1], [1, 1, 1], [3], [1, 2], [0.5, 0.5, 2]]
    tune = melody(rng, 8, rhythms, 0, 11, 4, steps=(-1, -1, 1, 1, 2, -2, 3))
    tune[3][-1] = (6, tune[3][-1][1])
    tune[7][-1] = (7, tune[7][-1][1])
    for b in range(72):
        croot, tones = prog[(b // 2) % 8]
        section = "intro" if b < 8 else "a" if b < 32 else "b" if b < 56 else "a2" if b < 68 else "end"
        # oom: the bass on one; pah-pah: the chord on two and three
        if section != "end" or b < 70:
            s.add(pizz(midi(root - 12 + croot), 0.7, seed=b), s.at(b), 0.5)
            for beat in (1, 2):
                for x in tones[1:3]:
                    s.add(pizz(midi(root + croot + x), 0.4, seed=b * 3 + beat), s.at(b, beat), 0.2)
        if section in ("a", "a2", "b"):
            notes = tune[(b - 8) % 8] if section != "b" else tune[(b + 3) % 8]
            t = 0.0
            for d, dur in notes:
                octave = 2 if section != "b" else 2
                m = scale_note(root, LYDIAN, d, octave)
                s.add(flute(midi(m), dur * s.beat * 1.05, seed=b * 7 + int(t * 2)), s.at(b, t), 0.4)
                t += dur
        if section == "b":
            if b % 2 == 0:
                s.add(strings([midi(root + croot + x) for x in tones], s.bar * 2 + 0.5, attack=0.6, voices=3, seed=b), s.at(b), 0.22)
            for i in range(3):
                s.add(bell(midi(scale_note(root, LYDIAN, (b * 3 + i * 2) % 9, 3)), 1.2), s.at(b, i), 0.07)
        if section == "b" and b >= 48 and b % 2 == 0:
            for x in (0, 4, 7):
                s.add(brass(midi(root - 12 + croot + x + 12), s.bar * 2, attack=0.25, bright=0.7, seed=b + x), s.at(b), 0.13)
        if b % 4 == 0 and section != "intro":
            s.add(triangle_hit(), s.at(b), 0.12)
    return s.finish(2.6, 0.3, 61)


def abyss():
    """The deep: a marimba rolling in six, a choir of "oo", whales far off, water dripping, and the sea floor under it all."""
    rng = random.Random(71)
    s = Song(162, 6, 52)                                    # six eighths to the bar, slow
    root = 47                                               # B2
    prog = [(0, [0, 3, 7, 14]), (-4, [0, 4, 7, 11]), (5, [0, 3, 7, 10, 14]), (7, [0, 5, 7, 10])]
    roll = [0, 7, 12, 7, 15, 7]
    for b in range(52):
        croot, tones = prog[(b // 2) % 4]
        if b % 2 == 0:
            s.add(choir([midi(root + 12 + croot + x) for x in tones[:3]], s.bar * 2 + 1.5, "u", attack=1.4, voices=3, seed=b), s.at(b), 0.42)
            s.add(sub(midi(root - 12 + croot), s.bar * 2 + 0.5, 0.8), s.at(b), 0.45)
        if 4 <= b < 48:
            for i, x in enumerate(roll):
                m = root + croot + x
                s.add(marimba(midi(m), 1.4, soft=True), s.at(b, i) + rng.uniform(-0.01, 0.01), 0.24 if i % 3 == 0 else 0.15)
        if 16 <= b < 40 and b % 4 == 0:
            # a slow line above, a single voice of the choir on "ah"
            for j, d in enumerate([4, 3, 2, 0] if b % 8 == 0 else [2, 4, 5, 4]):
                m = scale_note(root + 24, AEOLIAN, d)
                s.add(choir([midi(m)], s.bar * 0.9, "a", attack=0.5, voices=2, seed=b + j), s.at(b + j), 0.2)
        if b in (10, 26, 42):
            s.add(whale(midi(root + 19), midi(root + 12), 4.5, seed=b), s.at(b), 0.2)
        if rng.random() < 0.45:
            s.add(drip(seed=b), s.at(b, rng.uniform(0, 6)), 0.18)
    return s.finish(4.8, 0.45, 71)


def underworld():
    """The halls of the dead in five: taiko on the one and the four, an organ low, a choir lower, a cello's semitone, a bell."""
    rng = random.Random(81)
    s = Song(46, 5, 24)
    root = 37                                               # C#2
    prog = [(0, [0, 7, 12, 15]), (1, [0, 4, 7, 11]), (5, [0, 3, 7]), (7, [0, 5, 7, 12])]
    for b in range(24):
        croot, tones = prog[(b // 2) % 4]
        if b % 2 == 0:
            s.add(organ([midi(root + 12 + croot + x) for x in tones], s.bar * 2 + 0.6, attack=0.4), s.at(b), 0.32)
            s.add(choir([midi(root + 12 + croot), midi(root + 19 + croot)], s.bar * 2 + 1.0, "o", attack=1.6, voices=4, seed=b), s.at(b), 0.42)
        if 2 <= b < 22:
            s.add(taiko(0.9, seed=b), s.at(b, 0), 0.75)
            s.add(taiko(1.15, seed=b + 50), s.at(b, 3), 0.55)
            if b >= 8 and b % 2 == 1:
                s.add(taiko(1.4, seed=b + 90), s.at(b, 4.5), 0.3)
        if 6 <= b < 20:
            # the cello's lament: the semitone above the root, again and again, falling
            line = [(1, 2), (0, 1), (-1, 1), (0, 1)] if b % 2 == 0 else [(3, 1.5), (1, 1.5), (0, 2)]
            t = 0.0
            for d, dur in line:
                m = scale_note(root + 12, PHRYGIAN, d)
                s.add(strings([midi(m)], dur * s.beat + 0.3, attack=0.25, bright=0.7, voices=2, seed=b * 5 + d), s.at(b, t), 0.38)
                t += dur
        if b % 4 == 0:
            s.add(bell(midi(root + 36), 4.0), s.at(b, 2), 0.1)
        if b in (0, 8, 16):
            s.add(gong(55, 8.0, seed=b), s.at(b), 0.5)
    return s.finish(4.2, 0.4, 81)


def passage():
    """The puzzle halls in seven: an oud turning over a figure, a frame drum's doum and tek, a ney above, a clock ticking."""
    rng = random.Random(91)
    s = Song(200, 7, 60)                                    # eighths, 2+2+3
    root = 57                                               # A3
    figure = [0, 4, 1, 4, 0, 3, 1]
    groups = [0, 2, 4]
    for b in range(60):
        shift = [0, 0, -2, 1][(b // 4) % 4]
        s.add(sub(midi(root - 24), s.bar + 0.2, 0.05), s.at(b), 0.25 if b % 4 == 0 else 0.0)
        if 2 <= b < 58:
            for i, d in enumerate(figure):
                m = scale_note(root, HIJAZ, d + shift)
                s.add(oud(midi(m), 0.6, seed=b * 7 + i), s.at(b, i) + rng.uniform(-0.006, 0.006), 0.3 if i in groups else 0.2)
        if 6 <= b < 56:
            for i in range(7):
                if i in groups: s.add(doum(seed=b * 7 + i), s.at(b, i), 0.5 if i == 0 else 0.35)
                elif i in (3, 6) or rng.random() < 0.3: s.add(tek(seed=b * 7 + i), s.at(b, i), 0.25)
                s.add(shaker(seed=b * 11 + i), s.at(b, i + 0.5), 0.07)
        if 20 <= b < 48 and b % 2 == 0:
            line = melody(rng, 1, [[2, 2, 3], [3, 2, 2], [4, 3], [2, 5]], 0, 9, 4)[0]
            t = 0.0
            for d, dur in line:
                s.add(flute(midi(scale_note(root + 12, HIJAZ, d)), dur * s.beat * 1.1, breath=0.06, seed=b + d, ney=True), s.at(b, t), 0.28)
                t += dur
        if b % 2 == 0:
            for i in range(0, 7, 2):
                s.add(tick(seed=b + i), s.at(b, i), 0.12)
    return s.finish(2.2, 0.28, 91)


def trial():
    """A god's trial: strings driving in sixteenths, taiko and snare, brass stabs, and a choir over the top when it peaks."""
    rng = random.Random(101)
    s = Song(138, 4, 64)
    root = 40                                               # E2
    prog = [(0, [0, 3, 7]), (-4, [0, 4, 7]), (-2, [0, 4, 7]), (-5, [0, 4, 7, 10])]   # Em, C, D, B7
    for b in range(64):
        croot, tones = prog[(b // 2) % 4]
        section = 0 if b < 8 else 1 if b < 24 else 2 if b < 48 else 3 if b < 60 else 4
        if section < 4 or b < 62:
            # the ostinato: root, root, fifth, root... in sixteenths, a twist at the end of each bar
            for i in range(16):
                x = [0, 0, 7, 0, 0, 12, 7, 0, 0, 0, 7, 0, 3, 0, 5, 7][i] if section else [0, 0, 7, 0][i % 4]
                s.add(strings([midi(root + 12 + croot + x)], s.beat / 4 * 0.9, attack=0.005, bright=1.0, voices=2, seed=b * 16 + i), s.at(b, i / 4), 0.22)
        if section >= 1:
            s.add(taiko(1.0, seed=b), s.at(b, 0), 0.7)
            s.add(taiko(1.0, seed=b + 7), s.at(b, 1.5), 0.45)
            s.add(taiko(1.2, seed=b + 9), s.at(b, 2), 0.55)
        if section >= 2 and section < 4:
            for beat in (1, 3):
                s.add(snare(seed=b * 4 + beat), s.at(b, beat), 0.4)
            if b % 8 == 7:
                for i in range(8):
                    s.add(snare(seed=b * 30 + i, soft=0.4 + i * 0.07), s.at(b, 2 + i / 4), 0.35)
        if section >= 2 and b % 2 == 0:
            for beat in (0, 1.5, 3):
                for x in tones:
                    s.add(brass(midi(root + 24 + croot + x), s.beat * 0.6, attack=0.02, bright=1.1, seed=b + x), s.at(b, beat), 0.13)
        if section == 3 and b % 2 == 0:
            s.add(choir([midi(root + 24 + croot + x) for x in tones], s.bar * 2 + 0.3, "a", attack=0.3, voices=4, seed=b), s.at(b), 0.35)
        if b in (7, 23, 47):
            s.add(timpani(midi(root + 7), 1.6, seed=b), s.at(b, 3), 0.6)
        if b % 2 == 0:
            s.add(sub(midi(root - 12 + croot + 12), s.bar * 2, 0.02), s.at(b), 0.3)
    return s.finish(1.8, 0.22, 101)


def citadel():
    """A garrison's march: a snare drum's dotted beat and its rolls, horns in thirds, timpani, low strings striding."""
    rng = random.Random(111)
    s = Song(84, 4, 48)
    root = 46                                               # Bb2
    prog = [(0, [0, 4, 7]), (-2, [0, 4, 7]), (5, [0, 4, 7]), (0, [0, 4, 7, 10])]   # Bb, Ab, Eb, Bb7
    rhythms = [[1.5, 0.5, 1, 1], [1, 1, 2], [0.75, 0.25, 1, 2], [2, 1, 1]]
    tune = melody(rng, 8, rhythms, 0, 9, 4, steps=(-1, 1, 1, 2, -2, -1))
    tune[7][-1] = (0, tune[7][-1][1])
    for b in range(48):
        croot, tones = prog[(b // 2) % 4]
        # low strings, striding up the chord on every beat
        if b >= 2:
            for i, x in enumerate((0, 7, 12, 7)):
                s.add(strings([midi(root + croot + x)], s.beat * 0.85, attack=0.03, bright=0.6, voices=2, seed=b * 4 + i), s.at(b, i), 0.25)
        if b >= 4 and b < 46:
            for beat, gain in ((0, 0.45), (1.75, 0.25), (2, 0.4), (3.75, 0.25)):
                s.add(snare(seed=b * 8 + int(beat * 4)), s.at(b, beat), gain)
            if b % 4 == 3:
                for i in range(12):
                    s.add(snare(seed=b * 40 + i, soft=0.3 + i * 0.04), s.at(b, 3 + i / 12), 0.25)
            s.add(timpani(midi(root + croot), 1.4, seed=b), s.at(b, 0), 0.45)
            if b % 2 == 1: s.add(timpani(midi(root + croot + 7), 1.2, seed=b + 5), s.at(b, 2), 0.3)
        if 8 <= b < 44:
            notes = tune[(b - 8) % 8]
            t = 0.0
            for d, dur in notes:
                m = scale_note(root + 12, MIXO, d)
                s.add(brass(midi(m), dur * s.beat * 0.95, attack=0.04, bright=0.8, seed=b + d), s.at(b, t), 0.3)
                if b >= 24: s.add(brass(midi(m + 4 if d % 7 in (0, 3, 4) else m + 3), dur * s.beat * 0.95, attack=0.04, bright=0.7, seed=b + d + 1), s.at(b, t), 0.18)
                t += dur
    return s.finish(2.4, 0.3, 111)


def gate():
    """The dead gate: a choir in no hurry at all, glass harmonics turning slowly above it, the deep swelling under it."""
    rng = random.Random(121)
    s = Song(40, 4, 22)
    root = 56                                               # Ab3
    prog = [(0, [0, 4, 7, 11, 18]), (2, [0, 4, 7, 14]), (-3, [0, 3, 7, 10, 14]), (-7, [0, 4, 7, 11, 14])]
    for b in range(22):
        croot, tones = prog[(b // 2) % 4]
        if b % 2 == 0:
            s.add(choir([midi(root + croot + x) for x in tones[:4]], s.bar * 2 + 2.5, "a" if (b // 2) % 2 == 0 else "o", attack=3.0, voices=4, seed=b), s.at(b), 0.42)
            s.add(sub(midi(root - 24 + croot), s.bar * 2 + 1.0, 2.0), s.at(b), 0.35)
        # glass, high, a few notes to a bar, never on the beat
        for i in range(3):
            d = rng.choice([0, 1, 2, 4, 5, 6, 7, 9])
            m = scale_note(root + 24, LYDIAN, d)
            s.add(glass(midi(m), 4.0, 0.8), s.at(b, rng.uniform(0, 4)), 0.1)
        if b in (5, 13):
            s.add(riser(5.0, 300, 3000, seed=b) * 0.15, s.at(b, 0), 1.0)
        if b in (6, 14):
            s.add(gong(42, 8.0, seed=b), s.at(b), 0.3)
    return s.finish(5.5, 0.5, 121)


COMPOSED = {"hermitage": hermitage, "olympus": olympus, "abyss": abyss, "underworld": underworld,
            "passage": passage, "trial": trial, "citadel": citadel, "gate": gate}


# ------------------------------------------------------------------------------------------------ effects
def sfx():
    """Sound effects with weight: swells and choirs, deep blows and the breath before them, not only bells."""
    out = {}
    n = lambda s: np.zeros(int(s * SR))

    def finish(x, room=2.2, wet=0.35, seed=3):
        return compress(reverb(x, room, wet, seed), 0.5, 2.0)

    # the orb makes something: a breath drawn in, then it blooms - a harp sweeping up into a bright chord of voices
    x = n(2.6)
    place(x, riser(0.7, 400, 6000, seed=1) * 0.6, 0.0)
    for i, m in enumerate((62, 66, 69, 74, 78, 81, 86)):
        place(x, pluck(midi(m), 1.6, 0.7, seed=i), 0.62 + i * 0.035, 0.28)
    place(x, choir([midi(74), midi(78), midi(81)], 1.6, "a", attack=0.08, voices=3, seed=2), 0.7, 0.55)
    place(x, boom(1.2, 90, 50), 0.7, 0.35)
    place(x, glass(midi(93), 1.6, 0.05), 0.75, 0.12)
    out["orb_make"] = finish(x, 2.0, 0.35)
    # a core set in the orb: power rising through it, low to high, landing with a blow
    x = n(2.4)
    t = tvec(1.2)
    f = 45 * (8 ** (t / 1.2) )
    ph = TAU * np.cumsum(f) / SR
    hum = sum(np.sin(k * ph) / k for k in range(1, 10)) * (t / 1.2) ** 1.5
    place(x, lowpass(hum, 2500) * 0.5, 0.0)
    place(x, boom(1.5, 110, 40), 1.2, 0.9)
    place(x, taiko(1.0, seed=4), 1.2, 0.5)
    place(x, brass(midi(50), 1.0, attack=0.05), 1.2, 0.35)
    place(x, brass(midi(57), 1.0, attack=0.05), 1.2, 0.3)
    out["orb_core"] = finish(x, 1.8, 0.3)
    # a pattern shown: a rush of air past the ear, and glass ringing in it
    x = n(1.6)
    w = riser(0.6, 300, 5000, seed=5)
    place(x, (w + w[::-1] * 0.6) * 0.5, 0.0)
    for i, m in enumerate((81, 86, 88, 93)):
        place(x, glass(midi(m), 1.2, 0.04), 0.35 + i * 0.05, 0.16)
    out["orb_pattern"] = finish(x, 1.6, 0.35)
    # ichor poured in: a thick pour, bubbles rising through it, a warm swell of voices
    x = n(1.8)
    for i in range(9):
        f0 = 260 + (i % 3) * 60
        tt = tvec(0.12)
        bub = np.sin(TAU * np.cumsum(f0 * (1 + tt * 9)) / SR) * np.exp(-tt * 30)
        place(x, bub, i * 0.08 + random.Random(i).uniform(0, 0.03), 0.4)
    place(x, lowpass(noise(0.9, 6), 700) * np.linspace(1, 0, int(0.9 * SR)) * 0.3, 0.0)
    place(x, choir([midi(57), midi(64)], 1.3, "o", attack=0.25, voices=3, seed=7), 0.3, 0.45)
    out["orb_ichor"] = finish(x, 1.4, 0.3)
    # a shrine wakes: the ground answers with a gong, the columns with a rising choir, light with glass at the top
    x = n(4.5)
    place(x, gong(62, 4.5, seed=8), 0.0, 0.55)
    place(x, boom(2.5, 70, 32), 0.0, 0.6)
    place(x, choir([midi(50), midi(57), midi(62), midi(66), midi(69)], 3.6, "a", attack=1.2, voices=4, seed=9), 0.2, 0.7)
    for i, m in enumerate((74, 78, 81, 86, 90)):
        place(x, glass(midi(m), 2.6, 0.2), 1.0 + i * 0.18, 0.1)
    out["shrine_awaken"] = finish(x, 3.2, 0.42)
    # a keystone set: a great struck stone, and the horns answering it
    x = n(3.2)
    place(x, timpani(midi(33), 2.5, seed=10), 0.0, 0.8)
    place(x, gong(55, 3.0, seed=11), 0.0, 0.4)
    place(x, boom(2.0, 80, 35), 0.0, 0.6)
    for m in (45, 52, 57, 61):
        place(x, brass(midi(m + 12), 2.2, attack=0.25, bright=0.8, seed=m), 0.15, 0.2)
    place(x, choir([midi(69), midi(73)], 2.4, "o", attack=0.6, voices=3, seed=12), 0.3, 0.25)
    out["keystone_set"] = finish(x, 2.8, 0.38)
    # the gate wakes: the air drawn toward it, a blow that shakes the ground, a full choir and the horns, light pouring
    x = n(7.5)
    place(x, riser(2.0, 120, 5000, seed=13) * 0.9, 0.0)
    rise = tvec(2.0)
    place(x, np.sin(TAU * np.cumsum(40 * 4 ** (rise / 2.0)) / SR) * (rise / 2.0) ** 2 * 0.5, 0.0)
    place(x, boom(4.0, 75, 26), 2.0, 1.2)
    place(x, taiko(0.8, seed=14), 2.0, 0.9)
    place(x, gong(44, 5.5, seed=15), 2.0, 0.6)
    place(x, choir([midi(44), midi(51), midi(56), midi(60), midi(63), midi(68)], 5.0, "a", attack=0.4, voices=5, seed=16), 2.0, 0.9)
    for m in (44, 51, 56, 60):
        place(x, brass(midi(m + 12), 3.5, attack=0.15, bright=1.0, seed=m), 2.05, 0.18)
    for i, m in enumerate((80, 84, 87, 92, 96, 99)):
        place(x, glass(midi(m), 3.5, 0.3), 2.6 + i * 0.22, 0.09)
    out["gate_awaken"] = finish(x, 4.0, 0.45)
    # the screens' click: a small, soft knock of wood with a hint of a note
    x = n(0.14)
    tt = tvec(0.06)
    place(x, (np.sin(TAU * 820 * tt) * np.exp(-tt * 90) + bandpass(noise(0.06, 17), 800, 3000) * np.exp(-tt * 140) * 0.5), 0.0, 0.7)
    out["ui_click"] = x
    return out
