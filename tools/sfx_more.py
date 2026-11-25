"""More sound effects: the relics charging and striking, the moves of a fight, the mod's blocks being used, and the
small idle sounds machines and fires make - each made from synthesised layers (metal partials, filtered noise,
hums), with real instruments from the soundfont where an effect wants a musical note.

  relics      relic_cast_zeus / _poseidon / _hades   a breath of charge, then the power let go
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
def effects():
    out = {}
    # ---- the relics: a breath of charge, then the power let go
    x = blank(2.2)
    place(x, crackle(0.35, 120, 1, 8000) * np.linspace(0.2, 1, int(0.35 * SR)), 0.0, 0.6)
    place(x, riser(0.35, 400, 6000, 2), 0.0, 0.25)
    place(x, thunder(1.8, 3), 0.33, 1.0)
    place(x, boom(1.6, 80, 34), 0.33, 0.8)
    out["relic_cast_zeus"] = norm(fade(x, 0.005, 0.5))
    x = blank(2.2)
    place(x, whoosh(0.45, 200, 1200, 4), 0.0, 0.8)
    place(x, wave(1.8, 5), 0.25, 1.0)
    place(x, boom(1.4, 70, 30), 0.4, 0.5)
    out["relic_cast_poseidon"] = norm(fade(x, 0.005, 0.5))
    x = blank(2.2)
    place(x, souls(0.5, 6)[::-1] * 0.8, 0.0, 0.8)
    place(x, souls(1.6, 7), 0.45, 1.0)
    place(x, boom(1.6, 60, 26), 0.45, 0.8)
    place(x, crackle(1.0, 30, 8, 2500), 0.5, 0.3)
    out["relic_cast_hades"] = norm(fade(x, 0.005, 0.5))
    # a relic's shot: a zap, a jet of water, a dark streak
    x = blank(0.7)
    place(x, crackle(0.25, 200, 9, 9000), 0.0, 0.8)
    t = tvec(0.25)
    place(x, np.sin(TAU * np.cumsum(1800 * np.exp(-t * 8) + 200) / SR) * np.exp(-t * 10), 0.0, 0.5)
    out["relic_shot_zeus"] = norm(fade(x, 0.002, 0.2))
    out["relic_shot_poseidon"] = norm(fade(whoosh(0.6, 400, 2500, 10) + lowpass(noise(0.6, 11), 600) * np.exp(-tvec(0.6) * 6) * 0.8, 0.002, 0.2))
    x = whoosh(0.7, 150, 900, 12, up=False)
    place(x, souls(0.6, 13), 0.05, 0.4)
    out["relic_shot_hades"] = norm(fade(x, 0.002, 0.25))
    # a move charged again: a rising shimmer that settles on a bright note
    x = orchestra(lambda s: [s.note(s.instrument(CELESTA, 110), i * 0.06, 1.2, p, 90) for i, p in enumerate((79, 83, 86, 91))], 1.6, 0.6, 0.5)
    place(x, glass(1568, 1.2, 0.02), 0.2, 0.15)
    out["relic_ready"] = norm(fade(x, 0.002, 0.4))
    # the flourish: a heavy blow and the ring of the weapon
    x = blank(1.4)
    place(x, metal(420, 1.3, seed=14, ring=1.4), 0.0, 0.6)
    place(x, boom(0.8, 120, 50), 0.0, 0.7)
    place(x, lowpass(noise(0.2, 15), 1200) * np.exp(-tvec(0.2) * 25), 0.0, 0.8)
    out["relic_flourish"] = norm(fade(x, 0.002, 0.3))
    # a relic rises a level: horns and a bright choir in a rising chord
    def lvl(s):
        h, c = s.instrument(HORN, 110), s.instrument(CHOIR, 100)
        for i, p in enumerate((60, 64, 67, 72)): s.note(h, i * 0.12, 1.4 - i * 0.1, p, 100)
        s.chord(c, 0.36, 1.2, [72, 76, 79], 95)
    out["relic_level"] = norm(fade(orchestra(lvl, 2.0, 0.6, 0.5), 0.002, 0.5))
    # ---- a fight
    x = blank(1.2)
    place(x, metal(880, 1.1, ratios=(1.0, 2.4, 3.9, 5.6, 8.1), decay=3.0, seed=16, ring=1.3), 0.0, 0.8)
    place(x, metal(1320, 0.9, seed=17), 0.004, 0.4)
    out["parry"] = norm(fade(x, 0.001, 0.3))
    x = blank(0.8)
    place(x, whoosh(0.22, 1200, 7000, 18), 0.0, 0.8)
    place(x, lowpass(noise(0.25, 19), 2500) * np.exp(-tvec(0.25) * 20), 0.18, 0.9)
    place(x, boom(0.5, 140, 60), 0.18, 0.5)
    out["riposte"] = norm(fade(x, 0.001, 0.2))
    out["dodge"] = norm(fade(whoosh(0.4, 250, 2200, 20) + whoosh(0.4, 500, 4000, 21) * 0.5, 0.002, 0.1))
    # ---- the blocks
    t = tvec(1.4)
    grind = lowpass(noise(1.4, 22), 500) * (1 + 0.4 * np.sin(TAU * 7 * t)) + bandpass(noise(1.4, 23), 800, 2500) * 0.15
    x = grind * np.sin(np.pi * np.clip(t / 1.4, 0, 1)) ** 0.5
    place(x, boom(0.6, 90, 45), 1.1, 0.6)
    out["sliding_door"] = norm(fade(x, 0.05, 0.25))
    out["forge_strike"] = norm(fade(metal(620, 1.0, seed=24) + metal(310, 1.0, seed=26) * 0.5, 0.001, 0.3))
    x = metal(620, 1.6, seed=27, ring=1.6)
    place(x, orchestra(lambda s: s.chord(s.instrument(GLOCK, 110), 0.05, 1.2, [84, 88, 91], 100), 1.6, 0.5, 0.4), 0.0, 0.8)
    out["forge_perfect"] = norm(fade(x, 0.001, 0.4))
    x = metal(240, 0.9, decay=7, seed=28) * 0.7
    place(x, lowpass(noise(0.3, 29), 600) * np.exp(-tvec(0.3) * 12), 0.0, 0.8)
    out["forge_miss"] = norm(fade(x, 0.001, 0.3))
    def done(s):
        h, b = s.instrument(BRASS, 115), s.instrument(TUBULAR, 100)
        s.chord(h, 0.0, 1.6, [55, 62, 67, 71], 110)
        s.note(b, 0.0, 2.0, 79, 100)
    x = orchestra(done, 2.4, 0.6, 0.5)
    place(x, metal(620, 1.0, seed=30), 0.0, 0.5)
    out["forge_done"] = norm(fade(x, 0.002, 0.6))
    x = metal(180, 0.9, ratios=(1.0, 1.7, 2.9, 4.1), decay=6, seed=31) * 0.6
    place(x, lowpass(noise(0.15, 32), 1800) * np.exp(-tvec(0.15) * 30), 0.0, 1.0)
    out["seal_strike"] = norm(fade(x, 0.001, 0.25))
    x = blank(3.0)
    place(x, crackle(0.6, 80, 33, 5000), 0.0, 0.8)
    place(x, lowpass(noise(1.2, 34), 900) * np.exp(-tvec(1.2) * 3), 0.0, 0.8)
    place(x, boom(1.5, 70, 30), 0.0, 0.7)
    place(x, orchestra(lambda s: s.chord(s.instrument(CHOIR, 110), 0.1, 2.2, [57, 64, 69, 73], 105), 3.0, 0.7, 0.6), 0.0, 0.7)
    out["seal_break"] = norm(fade(x, 0.002, 0.8))
    def summon(s):
        hn, tb, c, tm = s.instrument(HORN, 120), s.instrument(TROMBONE, 115), s.instrument(CHOIR, 110), s.instrument(TIMPANI, 115)
        s.note(hn, 0.0, 2.2, 45, 120); s.note(tb, 0.0, 2.2, 33, 115); s.note(hn, 0.6, 1.6, 52, 110)
        s.chord(c, 0.4, 2.4, [45, 52, 57, 60], 105)
        for i in range(10): s.note(tm, i * 0.06, 0.1, 33, 50 + i * 7)
        s.note(tm, 0.6, 2.0, 33, 127)
    x = orchestra(summon, 3.5, 0.7, 0.6)
    place(x, boom(2.0, 60, 25), 0.6, 0.6)
    out["altar_summon"] = norm(fade(x, 0.005, 0.8))
    t = tvec(0.5)
    creak = np.sin(TAU * np.cumsum(180 + 120 * np.sin(TAU * 3 * t) + 40 * np.random.default_rng(35).normal(0, 1, len(t))) / SR) * np.sin(np.pi * t / 0.5) * 0.4
    x = blank(1.6)
    place(x, creak + bandpass(noise(0.5, 36), 300, 1500) * 0.3, 0.0, 0.8)
    place(x, orchestra(lambda s: [s.note(s.instrument(CELESTA, 110), 0.45 + i * 0.05, 1.0, p, 95) for i, p in enumerate((76, 79, 84, 88))], 1.6, 0.6, 0.5), 0.0, 0.8)
    out["cache_open"] = norm(fade(x, 0.002, 0.4))
    out["lock_pin"] = norm(fade(metal(3200, 0.12, decay=30, seed=37) * 0.6 + bandpass(noise(0.12, 38), 3000, 9000) * np.exp(-tvec(0.12) * 80), 0.0005, 0.04))
    x = metal(2600, 0.4, decay=14, seed=39)
    place(x, bandpass(noise(0.05, 40), 2000, 9000) * 2, 0.0, 1.0)
    place(x, metal(1800, 0.3, decay=20, seed=41) * 0.4, 0.12, 1.0)
    out["lock_break"] = norm(fade(x, 0.0005, 0.1))
    x = metal(500, 0.6, ratios=(1.0, 1.9, 3.3), decay=9, seed=42)
    place(x, lowpass(noise(0.1, 43), 1500) * np.exp(-tvec(0.1) * 40), 0.0, 1.2)
    place(x, metal(700, 0.5, decay=10, seed=44) * 0.5, 0.15, 1.0)
    out["lock_open"] = norm(fade(x, 0.0005, 0.15))
    t = tvec(1.5)
    x = hum(110, 1.5, seed=45) * (t / 1.5) + glass(880 * 1.5, 1.5, 0.5) * 0.3
    out["waygate_charge"] = norm(fade(x, 0.2, 0.3))
    x = blank(2.0)
    place(x, whoosh(1.2, 200, 6000, 46), 0.0, 1.0)
    place(x, orchestra(lambda s: s.chord(s.instrument(OOHS, 110), 0.3, 1.4, [69, 73, 76, 81], 100), 2.0, 0.8, 0.6), 0.0, 0.6)
    out["waygate_travel"] = norm(fade(x, 0.01, 0.5))
    x = orchestra(lambda s: (s.chord(s.instrument(OOHS, 110), 0.0, 2.0, [62, 69, 74], 100), s.note(s.instrument(CELESTA, 100), 0.2, 1.5, 86, 80)), 2.4, 0.8, 0.6)
    place(x, bandpass(noise(2.0, 47), 400, 2500) * np.sin(np.pi * tvec(2.0) / 2.0) * 0.25, 0.0, 1.0)
    out["oracle_speak"] = norm(fade(x, 0.05, 0.6))
    x = synth_taiko(0.7, seed=48, dur=1.6)
    place(x, boom(1.4, 70, 35), 0.0, 0.7)
    out["war_drum"] = norm(fade(x, 0.001, 0.4))
    x = lowpass(noise(0.3, 49), 900) * np.exp(-tvec(0.3) * 18)
    place(x, np.sin(TAU * 140 * tvec(0.25)) * np.exp(-tvec(0.25) * 25), 0.0, 0.6)
    out["dummy_hit"] = norm(fade(x, 0.001, 0.08))
    def solved(s):
        h, c = s.instrument(HARP, 110), s.instrument(CHOIR, 100)
        for i, p in enumerate((62, 66, 69, 74, 78, 81)): s.note(h, i * 0.07, 1.6, p, 95)
        s.chord(c, 0.3, 1.8, [62, 66, 69, 74], 95)
    out["puzzle_solved"] = norm(fade(orchestra(solved, 2.6, 0.7, 0.6), 0.002, 0.6))
    # ---- idle: what machines and fires do while nobody touches them (played now and then, so each fades in and out)
    def idle(x, secs, inn=0.4, outp=0.6):
        return norm(fade(x[:int(secs * SR)], inn, outp), 0.7)
    out["idle_orb"] = idle(hum(73, 3.0, (1, 0.6, 0.3, 0.2, 0.1), 0.4, 50) + glass(1175, 3.0, 0.8) * 0.15, 3.0)
    t = tvec(3.0)
    roar = lowpass(noise(3.0, 51), 350) * (1 + 0.3 * np.sin(TAU * 0.7 * t)) * 2
    out["idle_forge"] = idle(roar + crackle(3.0, 25, 52, 3500) * 1.5, 3.0, 0.3, 0.6)
    out["idle_gate"] = idle(hum(55, 3.0, (1, 0.7, 0.4, 0.3, 0.2), 1.2, 53) * (1 + 0.5 * np.sin(TAU * 1.1 * t)) + whoosh(3.0, 200, 900, 54) * 0.3, 3.0)
    out["idle_relay"] = idle(hum(120, 2.5, (1, 0.4, 0.3, 0.15), 2.0, 55) + crackle(2.5, 6, 56, 7000) * 0.6, 2.5)
    buzz = np.sign(np.sin(TAU * 100 * tvec(2.0))) * 0.25 + hum(100, 2.0, (1, 0.5), 3.0, 57)
    out["idle_jammer"] = idle(lowpass(buzz, 2500) + crackle(2.0, 20, 58, 8000) * 0.8, 2.0, 0.2, 0.4)
    out["idle_waygate"] = idle(hum(98, 3.0, (1, 0.5, 0.25, 0.25), 0.5, 59) + glass(784, 3.0, 1.0) * 0.2, 3.0)
    t = tvec(2.5)
    out["idle_fire"] = idle(lowpass(noise(2.5, 60), 500) * (1 + 0.4 * np.sin(TAU * 1.3 * t)) * 1.5 + crackle(2.5, 18, 61, 3000) * 1.4, 2.5, 0.3, 0.5)
    out["idle_oracle"] = idle(lowpass(noise(3.0, 62), 600) * 1.2 + bandpass(noise(3.0, 63), 500, 1800) * np.sin(TAU * 0.5 * tvec(3.0)) ** 2 * 0.6 + hum(147, 3.0, (1, 0.3), 0.3, 64) * 0.4, 3.0)
    def murmur(s):
        ch = s.instrument(OOHS, 70)
        s.chord(ch, 0.0, 3.0, [57, 64, 69], 60)
        s.swell(ch, 0.0, 3.0, 20, 80)
    out["idle_altar"] = idle(orchestra(murmur, 3.5, 0.9, 0.7), 3.5, 0.8, 1.0)
    out["idle_pedestal"] = idle(glass(659, 2.5, 0.6) * 0.5 + glass(988, 2.5, 0.9) * 0.3 + hum(82, 2.5, (1, 0.3), 0.6, 65) * 0.3, 2.5)
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
