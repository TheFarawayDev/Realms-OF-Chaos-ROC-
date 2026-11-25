"""More sound effects: the relics charging and striking, the moves of a fight, the mod's blocks being used, and the
small idle sounds machines and fires make - short, dry and crunchy like the game's own sounds: synthesised thuds,
clacks, clangs, whooshes, crackles and hums, each with a gravelly grain layer in it and crushed like an old sample,
with a note-block-like pling where something good happens.

  relics      relic_cast_zeus / _poseidon / _hades   a zap, a splash, a dark whoosh
              relic_shot_zeus / _poseidon / _hades   a relic's shot
              relic_ready                             a relic's move charged again
              relic_flourish, relic_level             the fourth blow of a chain; a relic rising a level
  a fight     parry, riposte, dodge
  blocks      sliding_door, forge_strike, forge_perfect, forge_miss, forge_done, seal_strike, seal_break,
              altar_summon, cache_open, lock_pin, lock_break, lock_open, waygate_charge, waygate_travel,
              oracle_speak, war_drum, dummy_hit, puzzle_solved
  idle        idle_orb, idle_forge, idle_gate, idle_relay, idle_jammer, idle_waygate, idle_fire, idle_oracle,
              idle_altar, idle_pedestal
"""
import random

import numpy as np

import compose
from compose import Score, render, SR, CHOIR, OOHS, HORN, BRASS, HARP, TUBULAR, CELESTA, GLOCK, TAIKO, TIMPANI, TRUMPET, TROMBONE, ORCH_HIT
from score import tvec, lowpass, bandpass, noise, boom as _boom, riser, gong, taiko as synth_taiko, glass, sub

TAU = 2 * np.pi


def boom(secs, f0, f1):
    """A deep blow (the synthesised one needs a second at least: cut to length)."""
    x = _boom(max(secs, 1.0), f0, f1)[:int(secs * SR)].copy()
    n = min(len(x), int(0.15 * SR))
    x[-n:] *= np.linspace(1, 0, n)
    return x


def norm(x, peak=0.9):
    return x / max(1e-6, np.max(np.abs(x))) * peak


def fade(x, inn=0.01, out=0.2):
    a, b = int(inn * SR), int(out * SR)
    if a: x[:a] *= np.linspace(0, 1, a)
    if b: x[-b:] *= np.linspace(1, 0, b) ** 2
    return x


def place(mix, sig, at, gain=1.0):
    i = int(at * SR)
    if i >= len(mix): return
    j = min(len(mix), i + len(sig))
    mix[i:j] += sig[:j - i] * gain


def blank(secs):
    return np.zeros(int(secs * SR))


def orchestra(build, secs, room=0.5, level=0.4):
    """A few notes from the soundfont, as a layer."""
    s = Score(60, 4, max(1, int(secs / 4) + 1), lead=0.0)
    build(s)
    x = render(s, room=room, level=level, gain=0.5)
    out = blank(secs)
    out[:min(len(out), len(x))] += x[:len(out)]
    return out


def metal(freq, secs, ratios=(1.0, 2.76, 5.4, 8.93, 13.3), decay=4.0, seed=0, ring=1.0):
    """A struck piece of metal: inharmonic partials dying at their own rates, with the click of the blow."""
    t = tvec(secs)
    rng = np.random.default_rng(seed)
    out = np.zeros(len(t))
    for k, r in enumerate(ratios):
        out += (1 / (k + 1)) * np.sin(TAU * freq * r * (1 + rng.uniform(-0.003, 0.003)) * t) * np.exp(-t * decay * (1 + k * 0.6) / ring)
    click = bandpass(noise(min(secs, 0.03), seed), 2000, 9000) * np.exp(-tvec(min(secs, 0.03)) * 200)
    out[:len(click)] += click * 1.5
    return out


def whoosh(secs, lo=300, hi=3000, seed=0, up=True):
    """Air moving past: noise swept through a band, swelling and falling."""
    t = tvec(secs)
    x = noise(secs, seed)
    out = np.zeros(len(t))
    steps = 16
    seg = len(t) // steps
    for i in range(steps):
        a, b = i * seg, (i + 1) * seg if i < steps - 1 else len(t)
        f = lo * (hi / lo) ** ((i / steps) if up else 1 - i / steps)
        out[a:b] = bandpass(x[a:b], f * 0.6, f * 1.6)
    return out * np.sin(np.pi * np.clip(t / secs, 0, 1)) ** 1.5


def crackle(secs, rate=40, seed=0, bright=4000):
    """Sparks and crackles: short bursts at random, each a little click of filtered noise."""
    rng = np.random.default_rng(seed)
    out = blank(secs)
    for _ in range(int(rate * secs)):
        n = int(rng.uniform(0.002, 0.012) * SR)
        i = rng.integers(0, max(1, len(out) - n))
        burst = rng.normal(0, 1, n) * np.exp(-np.arange(n) / n * 5) * rng.uniform(0.3, 1.0)
        out[i:i + n] += burst
    return bandpass(out, 600, bright)


def hum(freq, secs, harmonics=(1, 0.5, 0.25, 0.12), wobble=0.6, seed=0):
    """A machine's hum: a low tone and its overtones, beating slowly, with a little air in it."""
    t = tvec(secs)
    out = sum(a * np.sin(TAU * freq * (k + 1) * t + k) for k, a in enumerate(harmonics))
    out *= 1 + 0.25 * np.sin(TAU * wobble * t)
    out += lowpass(noise(secs, seed), 400) * 0.05
    return out


def thunder(secs, seed=0):
    """A crack of thunder: a bright splitting crack, then the rumble rolling away."""
    t = tvec(secs)
    crack = bandpass(noise(secs, seed), 1500, 9000) * np.exp(-t * 18)
    rumble = lowpass(noise(secs, seed + 1), 180) * np.exp(-t * 1.4) * (1 - np.exp(-t * 20)) * 6
    return crack * 1.2 + rumble


def wave(secs, seed=0):
    """A wave breaking: the swell of water drawn up, its crash and the hiss running back."""
    t = tvec(secs)
    body = lowpass(noise(secs, seed), 900)
    env = np.where(t < secs * 0.35, (t / (secs * 0.35)) ** 2, np.exp(-(t - secs * 0.35) * 3.2))
    hiss = bandpass(noise(secs, seed + 1), 2500, 9000) * np.where(t > secs * 0.3, np.exp(-(t - secs * 0.3) * 2.5), 0) * 0.5
    return body * env * 3 + hiss


def souls(secs, seed=0):
    """The dead let go: a chord of hollow voices sliding down, with breath through it."""
    t = tvec(secs)
    out = np.zeros(len(t))
    for f in (110, 165, 220, 277):
        glide = f * (1 - 0.25 * (t / secs))
        ph = TAU * np.cumsum(glide) / SR
        out += np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.2 * np.sin(3 * ph)
    breath = bandpass(noise(secs, seed), 300, 2400) * 0.4
    return (out / 4 + breath) * np.sin(np.pi * np.clip(t / secs, 0, 1)) ** 0.8


# ------------------------------------------------------------------------------------------------ the effects
# The effects are made the way the game's own are: short, dry (no hall around them), a chunky blow or click or
# whoosh at the heart, and a note-block "pling" where something good happens. The sounds heard over and over (the
# hammer, a hit, a pin, a parry) come in a few variants, one picked at random each time, as the game's own do.
VERSION = 3
TOP = 7500                       # the game's sounds are a little soft at the very top


def mix(*parts):
    """Layers of different lengths added together."""
    out = blank(max(len(p) for p in parts) / SR + 1 / SR)
    for p in parts: out[:len(p)] += p
    return out


def gravel(secs, seed=0, rate=1400, lo=600, hi=7000, decay=14):
    """The crunch at the heart of the game's sounds: hundreds of tiny grains of noise, like gravel or packed snow
    giving way, thickest at the blow and thinning out."""
    rng = np.random.default_rng(seed + 1000)
    n = int(secs * SR)
    out = np.zeros(n)
    k = int(rate * secs)
    for p, a in zip((rng.random(k) ** 1.8 * n).astype(int), rng.uniform(0.2, 1.0, k)):
        g = min(int(rng.uniform(0.0006, 0.004) * SR), n - p)
        if g > 0: out[p:p + g] += a * rng.normal(0, 1, g) * np.exp(-np.arange(g) / g * 4)
    return bandpass(out, lo, hi) * np.exp(-np.arange(n) / SR * decay)


def crush(x, bits=9, hold=2):
    """An old sampler's grit: fewer levels and held samples (a lower rate, its aliases left in), driven a little."""
    y = np.repeat(x[::hold], hold)[:len(x)]
    q = 2 ** (bits - 1)
    y = np.round(y * q) / q
    return np.tanh(y * 1.6) / np.tanh(1.6)


def dry(x, out=0.05, peak=0.9, bits=9):
    """Short, dry and crunchy: the top softened, then crushed like an old sample."""
    return norm(fade(crush(norm(lowpass(x, TOP), 1.0), bits), 0.002, out), peak)


def ping(m, secs=0.5, bright=0.6):
    """A note-block pling: a bell-like note with a quick, clean decay."""
    from music import bell, midi
    t = tvec(secs)
    x = bell(midi(m), secs) * 0.7 + np.sin(TAU * midi(m) * t) * np.exp(-t * 7) * 0.6
    return x * (1 - np.exp(-t * 400))


def harp(m, secs=0.4):
    from music import pluck, midi
    return pluck(midi(m), secs, 0.6)


def thud(f0, secs, seed=0, body=1.0):
    """A heavy blow on something solid: a falling low tone and a short burst of dull noise."""
    t = tvec(secs)
    tone = np.sin(TAU * np.cumsum(f0 * (0.55 + 0.45 * np.exp(-t * 30))) / SR) * np.exp(-t * 14)
    knock = lowpass(noise(secs, seed), 900) * np.exp(-t * 35)
    return tone * body + knock * 0.9 + gravel(secs, seed, 1200, 300, 3500, 16) * 1.4


def clack(f, secs, seed=0):
    """Wood or stone knocked: a few short resonances and a sharp tick."""
    t = tvec(secs)
    out = sum(a * np.sin(TAU * f * r * t) * np.exp(-t * d) for r, a, d in ((1, 1, 40), (2.3, 0.5, 60), (3.9, 0.3, 90)))
    return out + bandpass(noise(secs, seed), 1500, 6000) * np.exp(-t * 120) * 1.2 + gravel(secs, seed, 2500, 1200, 7000, 45) * 1.6


def clang(f, secs, seed=0):
    """An anvil-like clang: a metal strike cut short."""
    return metal(f, secs, ratios=(1.0, 2.4, 3.9, 5.6), decay=9, seed=seed, ring=1.0) + gravel(secs, seed, 1500, 1500, 7000, 30) * 0.9


def zap(secs, seed=0, f0=2200):
    t = tvec(secs)
    sweep = np.sign(np.sin(TAU * np.cumsum(f0 * np.exp(-t * 9) + 120) / SR)) * np.exp(-t * 12) * 0.35
    return lowpass(sweep, 5000) + crackle(secs, 260, seed, 8000) * np.exp(-t * 8)


def splash(secs, seed=0):
    """Water thrown: a burst of noise with the low of the water in it, and bubbles."""
    t = tvec(secs)
    x = bandpass(noise(secs, seed), 400, 5000) * np.exp(-t * 9) + lowpass(noise(secs, seed + 1), 500) * np.exp(-t * 6) * 1.5
    rng = np.random.default_rng(seed)
    for _ in range(int(secs * 18)):
        at, f = rng.uniform(0.05, secs * 0.8), rng.uniform(500, 1400)
        b = tvec(0.04)
        place(x, np.sin(TAU * np.cumsum(f * (1 + b * 12)) / SR) * np.exp(-b * 90) * 0.4, at)
    return x


def grains(secs, seed=0, rate=60, lo=200, hi=2500):
    """Stone crumbling: many short knocks of filtered noise, thinning out."""
    t = tvec(secs)
    return bandpass(crackle(secs, rate, seed, hi) * 3, lo, hi) * np.exp(-t * 3.5) + gravel(secs, seed, 1800, lo, hi, 4) * 2


def mumble(secs, seed=0, base=150):
    """A voice murmuring: a buzzing tone through two moving formants, rising and falling in syllables."""
    t = tvec(secs)
    rng = np.random.default_rng(seed)
    pitch = base * (1 + 0.15 * np.sin(TAU * 2.2 * t + rng.uniform(0, 6)))
    src = sum(np.sin(TAU * np.cumsum(pitch * k) / SR) / k for k in range(1, 12))
    out = bandpass(src, 300, 900) + bandpass(src, 1100, 2400) * 0.6
    syll = np.abs(np.sin(np.pi * t * 5.5)) ** 0.7
    return out * syll


def effects():
    out = {}
    v3 = lambda make: [make(i) for i in range(3)]
    # ---- the relics
    x = blank(0.9)
    place(x, zap(0.3, 1), 0.0, 0.8)
    place(x, thud(70, 0.8, 2, 1.4), 0.12, 1.0)
    place(x, crackle(0.6, 60, 3, 6000) * np.exp(-tvec(0.6) * 5), 0.12, 0.6)
    out["relic_cast_zeus"] = dry(x, 0.2)
    x = blank(0.8)
    place(x, whoosh(0.25, 300, 1500, 4), 0.0, 0.6)
    place(x, splash(0.6, 5), 0.15, 1.0)
    out["relic_cast_poseidon"] = dry(x, 0.2)
    x = blank(0.9)
    place(x, whoosh(0.3, 120, 700, 6, up=False), 0.0, 0.8)
    t = tvec(0.7)
    place(x, np.sin(TAU * np.cumsum(140 * (1 - 0.35 * t / 0.7)) / SR) * np.exp(-t * 4) * 0.6, 0.15)
    place(x, thud(55, 0.6, 7), 0.15, 0.9)
    out["relic_cast_hades"] = dry(x, 0.25)
    out["relic_shot_zeus"] = dry(zap(0.3, 9), 0.08)
    out["relic_shot_poseidon"] = dry(splash(0.35, 10) * 0.8 + whoosh(0.35, 500, 2500, 11) * 0.6, 0.08)
    out["relic_shot_hades"] = dry(whoosh(0.4, 150, 900, 12, up=False) + lowpass(noise(0.4, 13), 300) * np.exp(-tvec(0.4) * 8), 0.1)
    x = blank(0.6)
    place(x, ping(79, 0.3), 0.0, 0.8)
    place(x, ping(86, 0.5), 0.09, 1.0)
    out["relic_ready"] = dry(x, 0.15, 0.8, bits=11)
    x = thud(90, 0.5, 14, 1.3)
    place(x, clang(520, 0.4, 15) * 0.5, 0.0)
    out["relic_flourish"] = dry(x, 0.12)
    x = blank(0.9)
    for i, m in enumerate((72, 76, 79, 84)): place(x, ping(m, 0.6), i * 0.07, 0.8)
    out["relic_level"] = dry(x, 0.2, 0.85, bits=11)
    # ---- a fight
    out["parry"] = v3(lambda i: dry(mix(clang(700 + 90 * i, 0.35, 16 + i) * 0.8, clack(300 + 30 * i, 0.2, 20 + i) * 0.6), 0.1))
    x = blank(0.4)
    place(x, whoosh(0.15, 1200, 6000, 18), 0.0, 0.7)
    place(x, thud(120, 0.25, 19), 0.11, 1.0)
    out["riposte"] = dry(x, 0.08)
    out["dodge"] = v3(lambda i: dry(whoosh(0.22 + 0.03 * i, 300 + 80 * i, 2500 + 400 * i, 23 + i), 0.04, 0.7))
    # ---- the blocks
    t = tvec(0.6)
    x = blank(0.8)
    place(x, lowpass(noise(0.6, 26), 600) * (1 + 0.5 * np.sin(TAU * 18 * t)) * np.sin(np.pi * t / 0.6) ** 0.5, 0.0, 0.8)
    place(x, clack(110, 0.25, 27) + thud(80, 0.25, 28), 0.55, 1.0)
    out["sliding_door"] = dry(x, 0.08)
    out["forge_strike"] = v3(lambda i: dry(mix(clang(600 + 70 * i, 0.4, 30 + i), thud(200, 0.12, 33 + i) * 0.4), 0.1))
    x = clang(660, 0.4, 36)
    place(x, ping(91, 0.4), 0.05, 0.6)
    out["forge_perfect"] = dry(np.concatenate([x, blank(0.1)]), 0.15)
    out["forge_miss"] = dry(mix(thud(160, 0.3, 37), clack(240, 0.2, 38) * 0.5), 0.08)
    x = blank(0.9)
    place(x, clang(620, 0.4, 39), 0.0, 0.7)
    place(x, ping(79, 0.4), 0.15, 0.7)
    place(x, ping(84, 0.6), 0.27, 0.8)
    out["forge_done"] = dry(x, 0.2)
    out["seal_strike"] = v3(lambda i: dry(mix(clack(150 + 20 * i, 0.25, 40 + i), thud(110, 0.2, 43 + i) * 0.6), 0.06))
    x = blank(0.9)
    place(x, thud(70, 0.5, 46, 1.2), 0.0, 1.0)
    place(x, grains(0.85, 47), 0.0, 0.9)
    place(x, ping(81, 0.5) * 0.5 + ping(88, 0.5) * 0.4, 0.1, 0.5)
    out["seal_break"] = dry(x, 0.2)
    x = blank(1.5)
    t = tvec(1.1)
    place(x, lowpass(noise(1.1, 48), 250) * (t / 1.1) * 2 + np.sin(TAU * np.cumsum(55 + 50 * t / 1.1) / SR) * (t / 1.1) * 0.5, 0.0, 0.8)
    place(x, thud(60, 0.5, 49, 1.5), 1.0, 1.0)
    place(x, grains(0.5, 50), 1.0, 0.5)
    out["altar_summon"] = dry(x, 0.2)
    t = tvec(0.3)
    creak = np.sin(TAU * np.cumsum(220 + 90 * np.sin(TAU * 4 * t)) / SR) * np.sin(np.pi * t / 0.3) * 0.35
    x = blank(0.7)
    place(x, creak + clack(180, 0.3, 51) * 0.4, 0.0, 0.8)
    place(x, harp(76, 0.4), 0.22, 0.5)
    place(x, harp(83, 0.45), 0.3, 0.5)
    out["cache_open"] = dry(x, 0.12)
    out["lock_pin"] = v3(lambda i: dry(clack(1900 + 250 * i, 0.07, 52 + i), 0.02, 0.7))
    x = clack(1500, 0.12, 55)
    place(x, clack(1100, 0.12, 56) * 0.6, 0.06)
    out["lock_break"] = dry(np.concatenate([x, blank(0.08)]), 0.05)
    x = blank(0.35)
    place(x, clack(900, 0.12, 57), 0.0, 1.0)
    place(x, mix(clack(600, 0.15, 58), thud(180, 0.12, 59) * 0.4), 0.1, 0.9)
    out["lock_open"] = dry(x, 0.06)
    t = tvec(1.2)
    rise = np.sin(TAU * np.cumsum(180 + 380 * (t / 1.2) ** 1.5) / SR) * (t / 1.2) * 0.4
    out["waygate_charge"] = dry(rise + bandpass(noise(1.2, 60), 300, 2000) * (t / 1.2) * 0.4, 0.15)
    t = tvec(0.9)
    fall = np.sin(TAU * np.cumsum(900 * np.exp(-t * 3) + 120) / SR) * np.exp(-t * 3) * 0.4
    out["waygate_travel"] = dry(whoosh(0.9, 200, 3500, 61) + fall, 0.2)
    out["oracle_speak"] = dry(mumble(0.6, 62, 140) + lowpass(noise(0.6, 63), 400) * 0.15, 0.12)
    out["war_drum"] = dry(mix(thud(65, 0.6, 64, 1.6), clack(90, 0.15, 65) * 0.3), 0.15)
    out["dummy_hit"] = v3(lambda i: dry(mix(lowpass(noise(0.18, 66 + i), 1100 - 100 * i) * np.exp(-tvec(0.18) * 22), thud(130 + 15 * i, 0.15, 69 + i) * 0.5), 0.04))
    x = blank(0.9)
    for i, m in enumerate((74, 78, 81, 86)): place(x, harp(m, 0.5) * 0.7 + ping(m, 0.5) * 0.4, i * 0.09)
    out["puzzle_solved"] = dry(x, 0.2, bits=11)
    # ---- idle: short and quiet, played now and then
    def idle(x, secs, inn=0.15, outp=0.3):
        return norm(fade(crush(norm(lowpass(x[:int(secs * SR)], TOP), 1.0), 10), inn, outp), 0.6)
    out["idle_orb"] = idle(hum(73, 1.6, (1, 0.5, 0.2), 0.8, 72) + ping(86, 1.6) * 0.15, 1.6)
    t = tvec(1.2)
    out["idle_forge"] = idle(lowpass(noise(1.2, 73), 300) * 1.4 + crackle(1.2, 14, 74, 3000) * 1.6, 1.2, 0.1, 0.3)
    out["idle_gate"] = idle(hum(55, 2.0, (1, 0.6, 0.3), 1.5, 75) * (1 + 0.4 * np.sin(TAU * 1.5 * tvec(2.0))), 2.0, 0.3, 0.5)
    out["idle_relay"] = idle(hum(120, 1.2, (1, 0.3, 0.2), 3.0, 76) + crackle(1.2, 5, 77, 7000) * 0.5, 1.2)
    buzz = np.sign(np.sin(TAU * 100 * tvec(1.0))) * 0.2 + hum(100, 1.0, (1, 0.4), 4.0, 78)
    out["idle_jammer"] = idle(lowpass(buzz, 2000) + crackle(1.0, 12, 79, 7000) * 0.6, 1.0, 0.08, 0.2)
    out["idle_waygate"] = idle(hum(98, 1.8, (1, 0.4, 0.2), 0.7, 80) * (1 + 0.3 * np.sin(TAU * 2 * tvec(1.8))), 1.8, 0.3, 0.5)
    out["idle_fire"] = idle(lowpass(noise(1.0, 81), 400) * 0.8 + crackle(1.0, 12, 82, 3000) * 1.8, 1.0, 0.05, 0.25)
    out["idle_oracle"] = idle(lowpass(noise(1.5, 83), 500) * 1.0 + crackle(1.5, 6, 84, 2500) * 1.2 + hum(147, 1.5, (1, 0.3), 0.5, 85) * 0.3, 1.5)
    out["idle_altar"] = idle(mumble(1.4, 86, 110) * 0.5 + hum(55, 1.4, (1, 0.4), 0.6, 87) * 0.4, 1.4, 0.3, 0.5)
    out["idle_pedestal"] = idle(ping(76, 1.4) * 0.3 + ping(83, 1.4) * 0.2 + hum(82, 1.4, (1, 0.3), 0.6, 88) * 0.3, 1.4)
    return out


SUBTITLES = {
    "relic_cast_zeus": "Lightning is let loose", "relic_cast_poseidon": "The sea is let loose", "relic_cast_hades": "The dead are let loose",
    "relic_shot_zeus": "A bolt crackles", "relic_shot_poseidon": "Water rushes", "relic_shot_hades": "A shadow streaks",
    "relic_ready": "A relic's move is ready", "relic_flourish": "A flourish lands", "relic_level": "A relic grows stronger",
    "parry": "A blow is parried", "riposte": "A riposte strikes", "dodge": "Someone dodges",
    "sliding_door": "A stone door slides", "forge_strike": "The hammer rings", "forge_perfect": "A perfect strike",
    "forge_miss": "The hammer slips", "forge_done": "The forging is done", "seal_strike": "The seal is struck",
    "seal_break": "A seal breaks", "altar_summon": "A god is called", "cache_open": "A cache opens",
    "lock_pin": "A pin sets", "lock_break": "A lockpick snaps", "lock_open": "A lock opens",
    "waygate_charge": "A waygate gathers", "waygate_travel": "Someone passes through a waygate", "oracle_speak": "The Oracle speaks",
    "war_drum": "A war drum booms", "dummy_hit": "A dummy is struck", "puzzle_solved": "A puzzle is solved",
    "idle_orb": "An orb hums", "idle_forge": "The forge roars", "idle_gate": "The gate thrums", "idle_relay": "A relay hums",
    "idle_jammer": "A jammer buzzes", "idle_waygate": "A waygate hums", "idle_fire": "A fire crackles", "idle_oracle": "The Oracle's flame breathes",
    "idle_altar": "An altar murmurs", "idle_pedestal": "A pedestal glows",
}
