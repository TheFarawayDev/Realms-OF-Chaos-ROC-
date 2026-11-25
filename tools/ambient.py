"""The score, written the way the game's original soundtrack was: a felt piano played by hand (a little loose in time
and touch, its notes left ringing under the pedal), chords spread wide across the keys with their sevenths and
ninths, short simple motifs and long silences between them, soft analogue-sounding synths, and the whole thing
warmed on tape (a slow wobble, a little saturation, a soft top, a breath of hiss). The fights are the electronic
side of that sound: synth arpeggios, a round analogue bass and a crunchy drum machine. All of it original.

Each part is played into its own stem (so the piano can be softened like felt, a lead rounded off like an old synth,
the drums crushed like an old drum machine) and the stems are mixed and put to tape. The harmony underneath is
harmony.py's: one key per track, chord tones on the strong beats, every held note stopping at the chord change.

  hub          felt piano, a warm pad                                      D major     70
  hermitage    music box over a slow piano, birds                          F major     60
  olympus      piano waltz, glockenspiel, a round pulse-wave lead          G lydian    84  3/4
  olympus_peak running piano, synth strings, a saw lead, a soft kick       E minor     96
  abyss        electric piano in chorus, vibraphone, a watery pad          B minor     slow 6/8
  abyss_trench a deep bowed pad, low piano far apart, echoes               F# minor    50
  underworld   low piano in the dark, drones of voices and metal, embers   C minor     54
  tartarus     a crushed synth bass pulsing under metal pads               C# minor    66  5/4
  passage      kalimba arpeggios, electric piano, a ticking drum machine   D minor     92
  arena        pulse-wave lead, synth bass, drum machine                   D hijaz    126  9/8
  trial        saw lead, square arpeggios, pulsing bass, choir pad, drums  D minor    140
  summit       choir pad, synth brass, bells, big drums                    B minor    128
  wrath        fast saw arpeggios in 12/8, drums                           E harmonic 168
  titan        heavy synth bass, kick and toms, metal pad                  C minor     84  7/8
  oracle       celesta and piano over a halo pad                           E dorian    58  3/4
  citadel      synth brass and piano, a soft drum machine                  Bb major    96
  citadel_sea  electric piano rocking in six, a pad, the sea               G major     slow 6/8
  citadel_shade low piano and a bowed pad                                  F minor     60  3/4
  gate         the synthesised track (score.gate), kept as it was; gate_variant() is the same place in this manner
"""
import random

import numpy as np

from compose import Score, render, finish, SR, CELESTA, GLOCK, MUSICBOX, VIBES, KALIMBA, RHODES, WARM_PAD, HALO, SWEEP, BIRDS, SEASHORE
from harmony import Harmony, pad, melody, notes_in, MAJOR, MINOR, DORIAN, LYDIAN, HIJAZ, HARMONIC
from score import lowpass, bandpass, noise

PIANO, NEW_AGE, POLY, CHOIR_PAD, BOWED, METAL_PAD = 0, 88, 90, 91, 92, 93
SYN_STRINGS, SYN_BRASS, SQUARE, SAW, SYN_BASS, ECHOES = 50, 62, 80, 81, 38, 102
KICK, SNARE, RIM, CLAP, HAT, CRASH, LOW_TOM, MID_TOM, HI_TOM, SHAKER = 36, 38, 37, 39, 42, 49, 45, 47, 50, 70
KIT_808 = 25


# ------------------------------------------------------------------------------------------------ sound
def fit(x, n):
    return x[:n] if len(x) >= n else np.concatenate([x, np.zeros(n - len(x))])


def crush(x, bits=8, hold=3):
    """An old sampler's grit: fewer levels, and held samples (a lower rate, its aliases left in)."""
    y = np.repeat(x[::hold], hold)[:len(x)]
    q = 2 ** (bits - 1)
    return np.round(y * q) / q


def tape(x, seed, wow=0.0018, hiss=0.003):
    """To tape and back: a slow wobble in pitch, a little saturation, the top softened, a breath of hiss."""
    rng = np.random.default_rng(seed)
    n = len(x)
    t = np.arange(n) / SR
    d = wow * np.sin(2 * np.pi * 0.31 * t + rng.uniform(0, 6)) + 0.00004 * np.sin(2 * np.pi * 5.3 * t)
    y = np.interp(np.arange(n) + d * SR, np.arange(n), x)
    y = y / max(1e-6, np.abs(y).max())
    y = np.tanh(y * 1.4) / np.tanh(1.4)
    y = lowpass(y, 8000)
    return y + fit(bandpass(noise(n / SR + 0.01, seed), 1500, 8000), n) * hiss


class Song:
    """A track as stems: each a score of its own, rendered, shaped (softened, crushed) and mixed, then put to tape."""

    def __init__(self, bpm, per_bar, bars, seed):
        self.bpm, self.per_bar, self.bars, self.seed = bpm, per_bar, bars, seed
        self.rng = random.Random(seed)
        self.stems = []

    def stem(self, top=None, gain=1.0, room=0.85, level=0.8, crunch=False, chorus=False):
        s = Score(self.bpm, self.per_bar, self.bars)
        s.rng = random.Random(self.seed * 7 + len(self.stems))
        self.stems.append((s, top, gain, room, level, crunch, chorus))
        return s

    def mix(self, fade=6.0, wow=0.0018, hiss=0.003, extra=None):
        parts = []
        for s, top, gain, room, level, crunch, chorus in self.stems:
            if not any(m[0] & 0xF0 == 0x90 for _, _, m in s.events): continue
            x = render(s, room=room, damp=0.45, level=level, gain=0.5, chorus=chorus)
            if top: x = lowpass(x, top)
            if crunch: x = crush(x, *crunch) if isinstance(crunch, tuple) else crush(x)
            parts.append(x * gain)
        n = max(len(p) for p in parts)
        out = np.zeros(n)
        for p in parts: out[:len(p)] += p
        if extra is not None: out += fit(extra, n)
        return finish(tape(out, self.seed, wow, hiss), fade)


# ------------------------------------------------------------------------------------------------ playing
def spread(chord, lo):
    """A chord spread wide the way a pianist lays it out: the root low, its fifth, then the third and the colour
    (seventh or ninth) an octave and more above."""
    r = lo + (chord.root - lo) % 12
    ts = chord.tones
    second = r + 7 if 7 in ts else r + 12
    third = r + 12 + ts[1]
    colour = next((r + (e if e == 14 else 12 + e) for e in (11, 10, 14) if e in ts), r + 19)
    return sorted({r, second, third, colour})


def broken(s, ch, h, bars, lo, figure, vel=46, start_bar=0, hold=True, loose=0.025):
    """The chord broken by the hand, by a figure of (beat, which note of the spread chord); held under the pedal to
    the chord's end."""
    for b in range(start_bar, start_bar + bars):
        for beat, idx in figure:
            chord = h.at(b, beat)
            begun, size = h.span(b, beat)
            v = spread(chord, lo)
            dur = begun + size - beat - 0.04 if hold else min(0.9, begun + size - beat - 0.04)
            s.note(ch, s.at(b, beat), max(0.1, dur), v[min(idx, len(v) - 1)], vel + (5 if abs(beat - begun) < 1e-6 else 0), loose=loose)


def sing(s, ch, h, notes, vel=60, ring=1.6, loose=0.03):
    """A melody, each note left ringing a while (never past its chord)."""
    for b, beat, dur, m in notes:
        begun, size = h.span(b, beat)
        d = min(dur * ring if h.at(b, beat).has(m) else dur * 0.92, begun + size - beat - 0.04)
        s.note(ch, s.at(b, beat), max(0.1, d), m, vel + (4 if beat == 0 else 0), loose=loose)


def rests(notes, start, every=2, phrase=4, keep=1):
    """Silence between phrases: of every `every` phrases only `keep` are played."""
    return [n for n in notes if (((n[0] - start) // phrase) % every) < keep]


def roots(s, ch, h, bars, lo, beats, vel=80, dur=0.8, start_bar=0, octave_on=()):
    for b in range(start_bar, start_bar + bars):
        for i, beat in enumerate(beats):
            chord = h.at(b, beat)
            r = lo + (chord.root - lo) % 12
            s.note(ch, s.at(b, beat), dur, r + (12 if i in octave_on else 0), vel if i == 0 else vel - 14)


def arp(s, ch, h, bars, lo, hi, step, order, vel=64, start_bar=0, dur=None):
    for b in range(start_bar, start_bar + bars):
        for i in range(int(round(h.per_bar / step))):
            beat = i * step
            tones = notes_in(h.at(b, beat).pcs, lo, hi)
            begun, size = h.span(b, beat)
            d = max(0.05, min(dur or step * 0.9, begun + size - beat - 0.04))
            if tones: s.note(ch, s.at(b, beat), d, tones[order[i % len(order)] % len(tones)], vel + (8 if i % len(order) == 0 else 0))


# ------------------------------------------------------------------------------------------------ calm places
def hub():
    song = Song(70, 4, 40, 501)
    h = Harmony(2, MAJOR, ["Dadd9", "Gadd9", "Dadd9", "Gadd9", "Bm7", "Aadd9", "Gadd9", "Gadd9"] * 5, 4)
    p, pd = song.stem(top=3600), song.stem(top=5000, gain=0.7)
    piano, warm = p.instrument(PIANO, 100, 64, 90), pd.instrument(WARM_PAD, 80, 64, 100)
    broken(p, piano, h, 40, 38, [(0, 0), (1, 1), (1.5, 2), (2.5, 3), (3, 2)], 44)
    pad(pd, warm, h, 34, 57, 69, vel=46, swell=(25, 80), start_bar=4)
    line = melody(song.rng, h, 28, [[3, 1], [4], [2, 2], [1, 3]], 74, 86, (0, 2), start_bar=8)
    sing(p, piano, h, rests(line, 8), 58)
    return song.mix()


def hermitage():
    song = Song(60, 4, 32, 511)
    h = Harmony(5, MAJOR, ["F", "Am7", "Bbadd9", "F", "Dm7", "Bbadd9", "Csus4", "C"] * 4, 4)
    p, mb, pd, fx = song.stem(top=3400), song.stem(top=6000, gain=0.6), song.stem(top=4500, gain=0.6), song.stem(gain=0.35)
    piano, box, soft, birds = p.instrument(PIANO, 96, 60, 95), mb.instrument(MUSICBOX, 100, 70, 95), pd.instrument(NEW_AGE, 70, 64, 100), fx.instrument(BIRDS, 80, 90, 80)
    broken(p, piano, h, 32, 41, [(0, 0), (2, 1), (3, 2)], 40)
    pad(pd, soft, h, 28, 53, 67, vel=40, swell=(20, 70), start_bar=4)
    line = melody(song.rng, h, 24, [[1, 1, 2], [2, 2], [1.5, 0.5, 2], [4]], 77, 89, (0, 2), start_bar=6)
    sing(mb, box, h, rests(line, 6), 70, ring=1.2)
    for b in range(3, 32, 7): fx.note(birds, fx.at(b, song.rng.uniform(0, 3)), 1.2, 77, 50)
    return song.mix()


def olympus():
    song = Song(84, 3, 48, 521)
    h = Harmony(7, LYDIAN, ["Gadd9", "A", "Em7", "Gadd9", "Cadd9", "D", "Em7", "D"] * 6, 3)
    p, gl, ld, pd = song.stem(top=4200), song.stem(top=6000, gain=0.45), song.stem(top=1700, gain=0.7), song.stem(top=5000, gain=0.6)
    piano, glock, lead, halo = p.instrument(PIANO, 100, 60, 85), gl.instrument(GLOCK, 90, 80, 95), ld.instrument(SQUARE, 80, 50, 90), pd.instrument(HALO, 80, 64, 105)
    broken(p, piano, h, 48, 43, [(0, 0), (1, 2), (2, 3)], 44, hold=False)
    pad(pd, halo, h, 44, 55, 69, vel=42, swell=(20, 75), start_bar=4)
    line = melody(song.rng, h, 32, [[2, 1], [3], [1, 1, 1], [1.5, 0.5, 1]], 72, 86, (0,), start_bar=8, cadence=[3])
    sing(ld, lead, h, rests(line, 8), 66, ring=1.0)
    sing(gl, glock, h, [n for n in rests(line, 8, every=2, keep=1) if n[1] == 0], 56, ring=1.0)
    return song.mix()


def abyss():
    song = Song(132, 6, 40, 541)
    h = Harmony(11, MINOR, ["Bm7", "Gadd9", "Dadd9", "A", "Bm7", "Em7", "Gadd9", "F#"] * 5, 6)
    ep, vb, pd = song.stem(top=3800, chorus=True), song.stem(top=5000, gain=0.55), song.stem(top=4000, gain=0.7, room=0.95)
    rhodes, vibes, sweep = ep.instrument(RHODES, 100, 60, 100, chorus=90), vb.instrument(VIBES, 90, 74, 105), pd.instrument(SWEEP, 76, 64, 110)
    broken(ep, rhodes, h, 40, 35, [(0, 0), (1.5, 1), (3, 2), (4.5, 3)], 50)
    pad(pd, sweep, h, 36, 54, 66, vel=44, swell=(20, 75), start_bar=2)
    line = melody(song.rng, h, 24, [[3, 3], [6], [2, 1, 3]], 66, 80, (0, 3), start_bar=8, cadence=[6])
    sing(vb, vibes, h, rests(line, 8), 62, ring=1.3)
    return song.mix(wow=0.0024)


def abyss_trench():
    song = Song(50, 4, 24, 551)
    h = Harmony(6, MINOR, ["F#m", "Dadd9", "Bm7", "C#", "F#m", "Aadd9", "Dadd9", "C#"] * 3, 4)
    p, pd, fx = song.stem(top=2600), song.stem(top=2500, room=1.0, level=0.9), song.stem(top=4000, gain=0.3, room=1.0)
    piano, deep, echo = p.instrument(PIANO, 100, 56, 115), pd.instrument(BOWED, 90, 64, 120), fx.instrument(ECHOES, 70, 80, 127)
    pad(pd, deep, h, 24, 42, 57, vel=58, swell=(25, 90))
    rng = song.rng
    for b in range(24):
        v = spread(h.at(b, 0), 30)
        if b % 2 == 0: p.note(piano, p.at(b, rng.choice([0, 1])), 3.8, v[0], 52, loose=0.05)
        if b % 3 == 1: p.note(piano, p.at(b, 2), 1.9, v[2] + 12, 40, loose=0.05)
    for b in (5, 13, 21): fx.note(echo, fx.at(b), 6.0, 66, 50)
    return song.mix(wow=0.0028, hiss=0.004)


def underworld():
    song = Song(54, 4, 32, 561)
    h = Harmony(0, MINOR, ["Cm", "Abadd9", "Fm7", "G", "Cm", "Ab", "Fm", "G"] * 4, 4)
    p, dr, mt = song.stem(top=2400), song.stem(top=2200, room=0.95), song.stem(top=3000, gain=0.45, room=1.0)
    piano, choir, metal = p.instrument(PIANO, 100, 60, 105), dr.instrument(CHOIR_PAD, 84, 64, 110), mt.instrument(METAL_PAD, 80, 64, 115)
    pad(dr, choir, h, 32, 43, 58, vel=56, swell=(30, 95))
    pad(mt, metal, h, 24, 55, 67, vel=40, swell=(10, 60), start_bar=8)
    broken(p, piano, h, 32, 31, [(0, 0), (2.5, 1)], 50, loose=0.04)
    line = melody(song.rng, h, 24, [[4], [3, 1], [2, 2]], 55, 67, (0, 2), start_bar=8)
    sing(p, piano, h, rests(line, 8), 52)
    from sfx_more import crackle
    embers = crackle(song.bars * 4 * 60 / 54 + 8, 3, 7, 2200) * 0.05
    return song.mix(wow=0.0022, hiss=0.004, extra=embers)


def tartarus():
    song = Song(66, 5, 30, 571)
    h = Harmony(1, MINOR, ["C#m", "Aadd9", "F#m7", "G#", "C#m", "E", "Aadd9", "G#"] * 4, 5)
    bs, mt, p = song.stem(top=900, crunch=(9, 2)), song.stem(top=3000, room=1.0), song.stem(top=2600)
    synb, metal, piano = bs.instrument(SYN_BASS, 100, 64, 60), mt.instrument(METAL_PAD, 90, 64, 115), p.instrument(PIANO, 100, 56, 100)
    pad(mt, metal, h, 30, 52, 64, vel=52, swell=(25, 90))
    roots(bs, synb, h, 28, 25, (0, 1.5, 3, 4), vel=100, dur=0.6, start_bar=2)
    line = melody(song.rng, h, 24, [[3, 2], [5], [2, 3]], 49, 61, (0, 3), start_bar=4, cadence=[5])
    sing(p, piano, h, rests(line, 4), 60)
    return song.mix(hiss=0.004)


def passage():
    song = Song(92, 4, 40, 581)
    h = Harmony(2, MINOR, ["Dm7", "Gm7", "C", "Fadd9", "Bbadd9", "Gm7", "A", "Dm"] * 5, 4)
    k, ep, pd, dr = song.stem(top=5000), song.stem(top=3200, chorus=True), song.stem(top=4000, gain=0.5), song.stem(top=6000, gain=0.6, crunch=(8, 3))
    kal, rhodes, warm, kit = k.instrument(KALIMBA, 96, 50, 75), ep.instrument(RHODES, 96, 76, 85, chorus=70), pd.instrument(WARM_PAD, 70, 64, 95), dr.drums(KIT_808, 90, 30)
    arp(k, kal, h, 40, 62, 86, 0.5, [0, 2, 1, 3, 2, 4, 3, 1], 56)
    broken(ep, rhodes, h, 38, 38, [(0, 0), (2, 1)], 52, start_bar=2)
    pad(pd, warm, h, 36, 57, 69, vel=40, swell=(20, 65), start_bar=4)
    line = melody(song.rng, h, 24, [[1, 1, 2], [2, 2], [1.5, 0.5, 2]], 65, 79, (0, 2), start_bar=8)
    sing(ep, rhodes, h, rests(line, 8), 66, ring=1.1)
    for b in range(6, 38):
        for i in range(4): dr.note(kit, dr.at(b, i + 0.5), 0.05, HAT, 46)
        dr.note(kit, dr.at(b, 0), 0.2, KICK, 70)
        dr.note(kit, dr.at(b, 2), 0.1, RIM, 50)
    return song.mix()


def oracle():
    song = Song(58, 3, 40, 591)
    h = Harmony(4, DORIAN, ["Em7", "D", "A", "Em7", "G", "D", "A", "Bm7"] * 5, 3)
    c, p, pd = song.stem(top=6000, gain=0.6), song.stem(top=3400), song.stem(top=4500, gain=0.7, room=1.0)
    cel, piano, halo = c.instrument(CELESTA, 90, 50, 105), p.instrument(PIANO, 100, 70, 100), pd.instrument(HALO, 84, 64, 115)
    pad(pd, halo, h, 38, 52, 66, vel=46, swell=(25, 80))
    arp(c, cel, h, 40, 64, 88, 1.0, [0, 2, 1], 48, dur=1.8)
    broken(p, piano, h, 38, 40, [(0, 0)], 46, start_bar=2)
    line = melody(song.rng, h, 24, [[2, 1], [3], [1, 2]], 64, 78, (0,), start_bar=8, cadence=[3])
    sing(p, piano, h, rests(line, 8), 58)
    return song.mix(wow=0.002)


def citadel():
    song = Song(96, 4, 40, 601)
    h = Harmony(10, MAJOR, ["Bb", "Ebadd9", "F", "Gm7", "Ebadd9", "Bb", "Fsus4", "F"] * 5, 4)
    br, p, dr = song.stem(top=2600), song.stem(top=3800), song.stem(top=5500, gain=0.55, crunch=(8, 3))
    brass, piano, kit = br.instrument(SYN_BRASS, 90, 50, 80), p.instrument(PIANO, 100, 76, 80), dr.drums(KIT_808, 90, 35)
    pad(br, brass, h, 36, 53, 67, vel=52, swell=(35, 95), start_bar=4, size=3)
    broken(p, piano, h, 40, 34, [(0, 0), (1, 1), (2, 2), (3, 1)], 46, hold=False)
    line = melody(song.rng, h, 28, [[1.5, 0.5, 2], [1, 1, 2], [2, 2]], 65, 79, (0, 2), start_bar=8)
    sing(p, piano, h, rests(line, 8, every=3, keep=2), 64, ring=1.1)
    for b in range(4, 38):
        dr.note(kit, dr.at(b, 0), 0.2, KICK, 76)
        dr.note(kit, dr.at(b, 2), 0.2, KICK, 64)
        dr.note(kit, dr.at(b, 1), 0.1, RIM, 46)
        dr.note(kit, dr.at(b, 3), 0.1, CLAP, 56)
    return song.mix()


def citadel_sea():
    song = Song(132, 6, 48, 611)
    h = Harmony(7, MAJOR, ["Gadd9", "Cadd9", "D", "Em7", "Cadd9", "G", "Dsus4", "D"] * 6, 6)
    ep, pd, p, fx = song.stem(top=3600, chorus=True), song.stem(top=4000, gain=0.55), song.stem(top=3600, gain=0.9), song.stem(top=3000, gain=0.25)
    rhodes, sweep, piano, sea = ep.instrument(RHODES, 96, 54, 90, chorus=80), pd.instrument(SWEEP, 70, 64, 105), p.instrument(PIANO, 96, 74, 90), fx.instrument(SEASHORE, 90, 64, 90)
    broken(ep, rhodes, h, 48, 31, [(0, 0), (1, 1), (2, 2), (3, 3), (4, 2), (5, 1)], 46, hold=False)
    pad(pd, sweep, h, 44, 55, 67, vel=40, swell=(20, 70), start_bar=4)
    line = melody(song.rng, h, 32, [[3, 3], [2, 1, 3], [6]], 67, 81, (0, 3), start_bar=8, cadence=[6])
    sing(p, piano, h, rests(line, 8), 60)
    for b in range(0, 48, 4): fx.note(sea, fx.at(b), 20.0, 55, 60)
    return song.mix()


def citadel_shade():
    song = Song(60, 3, 40, 621)
    h = Harmony(5, MINOR, ["Fm", "Dbadd9", "Bbm7", "C", "Fm", "Abadd9", "Bbm", "C"] * 5, 3)
    p, bw = song.stem(top=2800), song.stem(top=3000, room=0.95)
    piano, bowed = p.instrument(PIANO, 100, 58, 100), bw.instrument(BOWED, 86, 64, 110)
    pad(bw, bowed, h, 38, 48, 62, vel=52, swell=(25, 85))
    broken(p, piano, h, 40, 29, [(0, 0), (1, 1), (2, 2)], 42)
    line = melody(song.rng, h, 24, [[2, 1], [3], [1, 1, 1]], 53, 67, (0,), start_bar=8, cadence=[3])
    sing(p, piano, h, rests(line, 8), 58)
    return song.mix(wow=0.0022)


def olympus_peak():
    song = Song(96, 4, 40, 531)
    h = Harmony(4, MINOR, ["Em", "Cadd9", "G", "D", "Em", "Cadd9", "Am7", "B"] * 5, 4)
    p, st, ld, bs, dr = song.stem(top=4000), song.stem(top=4000, gain=0.8), song.stem(top=2200, gain=0.7), song.stem(top=700, gain=0.8), song.stem(top=5000, gain=0.6, crunch=(8, 3))
    piano, strings, saw, synb, kit = p.instrument(PIANO, 100, 60, 80), st.instrument(SYN_STRINGS, 90, 64, 90), ld.instrument(SAW, 80, 50, 80), bs.instrument(SYN_BASS, 96, 64, 40), dr.drums(KIT_808, 90, 35)
    broken(p, piano, h, 40, 40, [(i * 0.5, [0, 1, 2, 3, 2, 1, 2, 1][i]) for i in range(8)], 46, hold=False)
    pad(st, strings, h, 36, 52, 67, vel=56, swell=(35, 100), start_bar=4)
    roots(bs, synb, h, 32, 28, (0, 2), vel=86, dur=1.8, start_bar=8)
    line = melody(song.rng, h, 24, [[2, 1, 1], [1.5, 0.5, 2], [4]], 67, 81, (0, 2), start_bar=12)
    sing(ld, saw, h, rests(line, 12), 70, ring=1.0)
    for b in range(8, 38):
        for beat in (0, 2): dr.note(kit, dr.at(b, beat), 0.2, KICK, 76)
        dr.note(kit, dr.at(b, 3), 0.1, CLAP, 50)
    return song.mix()


# ------------------------------------------------------------------------------------------------ fights
def arena():
    song = Song(252, 9, 64, 631)
    h = Harmony(2, HIJAZ, ["D", "Eb", "D", "Gm", "D", "Eb", "Cm", "D"] * 8, 9)
    ld, bs, pd, dr = song.stem(top=2600), song.stem(top=900), song.stem(top=4000, gain=0.5), song.stem(top=6000, gain=0.8, crunch=(8, 3))
    lead, synb, poly, kit = ld.instrument(SQUARE, 80, 50, 60), bs.instrument(SYN_BASS, 100, 64, 30), pd.instrument(POLY, 70, 64, 70), dr.drums(KIT_808, 100, 30)
    groups = (0, 2, 4, 6)
    pad(pd, poly, h, 60, 55, 69, vel=44, swell=None, start_bar=4)
    roots(bs, synb, h, 64, 38, groups, vel=100, dur=0.8)
    for b in range(4, 64):
        for g in groups: dr.note(kit, dr.at(b, g), 0.2, KICK if g in (0, 4) else CLAP, 100 if g == 0 else 74)
        for i in range(9): dr.note(kit, dr.at(b, i + 0.5), 0.05, HAT, 44)
    sing(ld, lead, h, melody(song.rng, h, 16, [[2, 2, 2, 3], [1, 1, 2, 2, 3], [4, 2, 3]], 62, 76, groups, start_bar=8, cadence=[9]), 84, ring=1.0)
    sing(ld, lead, h, melody(random.Random(632), h, 16, [[2, 2, 2, 3], [3, 3, 3]], 64, 79, groups, start_bar=32, cadence=[9]), 88, ring=1.0)
    return song.mix(wow=0.001)


def trial():
    song = Song(140, 4, 64, 641)
    prog = ["Dm", "Bb", "F", "C", "Dm", "Bb", "Gm", "A", "Dm", "F", "C", "Bb", "Gm", "Bb", "A", "A"]
    h = Harmony(2, MINOR, prog * 4, 4)
    ld, ar, bs, ch, dr = song.stem(top=3000), song.stem(top=3600, gain=0.7), song.stem(top=1000), song.stem(top=4000, gain=0.8), song.stem(top=6500, gain=0.9, crunch=(8, 2))
    saw, square, synb, choir, kit = ld.instrument(SAW, 86, 50, 50), ar.instrument(SQUARE, 76, 76, 50), bs.instrument(SYN_BASS, 104, 64, 30), ch.instrument(CHOIR_PAD, 84, 64, 70), dr.drums(0, 104, 30)
    for b in range(64):
        root = 38 + (h.at(b, 0).root - 38) % 12
        for i in range(8): bs.note(synb, bs.at(b, i * 0.5), 0.4, root + (12 if i % 4 == 3 else 0), 104 if i % 2 == 0 else 84)
        if b >= 8:
            for beat in (0, 1.5, 2, 3.25): dr.note(kit, dr.at(b, beat), 0.2, KICK, 106)
            for beat in (1, 3): dr.note(kit, dr.at(b, beat), 0.2, SNARE, 98)
            for i in range(8): dr.note(kit, dr.at(b, i * 0.5), 0.05, HAT, 56)
            if b % 4 == 0: dr.note(kit, dr.at(b, 0), 1.5, CRASH, 90)
    arp(ar, square, h, 56, 62, 81, 0.25, [0, 1, 2, 1, 2, 3, 2, 1], 70, start_bar=8, dur=0.2)
    pad(ch, choir, h, 48, 55, 72, vel=60, swell=(40, 110), start_bar=16)
    theme = melody(song.rng, h, 16, [[1.5, 0.5, 2], [1, 1, 2], [3, 1]], 62, 77, (0, 2), start_bar=24)
    sing(ld, saw, h, theme, 96, ring=1.0)
    sing(ld, saw, h, [(b + 16, beat, dur, m + 12 if m < 70 else m) for b, beat, dur, m in theme if b + 16 < 64], 100, ring=1.0)
    return song.mix(wow=0.0008, hiss=0.002)


def summit():
    song = Song(128, 4, 64, 651)
    prog = ["Bm", "G", "D", "A", "Bm", "Em", "F#", "F#", "G", "D", "A", "Bm", "G", "A", "F#", "Bm"]
    h = Harmony(11, MINOR, prog * 4, 4)
    ch, br, bl, bs, dr = song.stem(top=4000), song.stem(top=2800), song.stem(top=6000, gain=0.5), song.stem(top=1000), song.stem(top=6500, gain=0.9, crunch=(8, 2))
    choir, brass, bells, synb, kit = ch.instrument(CHOIR_PAD, 96, 64, 75), br.instrument(SYN_BRASS, 96, 50, 60), bl.instrument(GLOCK, 90, 80, 80), bs.instrument(SYN_BASS, 100, 64, 30), dr.drums(0, 104, 40)
    pad(ch, choir, h, 64, 54, 73, vel=76, swell=(60, 120))
    roots(bs, synb, h, 64, 35, (0, 1, 2, 3), vel=96)
    for b in range(64):
        if b >= 8:
            for beat in (0, 2.5): dr.note(kit, dr.at(b, beat), 0.2, KICK, 110)
            for beat in (1, 3): dr.note(kit, dr.at(b, beat), 0.2, SNARE, 100)
            if b % 4 == 3:
                for beat, tom in ((3.25, HI_TOM), (3.5, MID_TOM), (3.75, LOW_TOM)): dr.note(kit, dr.at(b, beat), 0.15, tom, 96)
            if b % 4 == 0: dr.note(kit, dr.at(b, 0), 1.5, CRASH, 96)
        if b % 2 == 0: bl.note(bells, bl.at(b, 0), 2.0, 71 + (h.at(b, 0).root - 71) % 12, 70)
    chant = melody(song.rng, h, 16, [[2, 2], [1, 1, 2], [3, 1]], 62, 76, (0, 2), start_bar=16)
    sing(br, brass, h, chant, 96, ring=1.0)
    sing(br, brass, h, [(b + 32, beat, dur, m) for b, beat, dur, m in chant if b + 32 < 64], 100, ring=1.0)
    return song.mix(wow=0.0008, hiss=0.002)


def wrath():
    song = Song(504, 12, 56, 661)
    h = Harmony(4, HARMONIC, ["Em", "C", "Am", "B", "Em", "G", "C", "B"] * 7, 12)
    ar, bs, ch, dr = song.stem(top=3000), song.stem(top=1000), song.stem(top=4000, gain=0.8), song.stem(top=6000, gain=0.9, crunch=(8, 2))
    saw, synb, choir, kit = ar.instrument(SAW, 74, 60, 55), bs.instrument(SYN_BASS, 104, 64, 30), ch.instrument(CHOIR_PAD, 90, 64, 65), dr.drums(KIT_808, 105, 30)
    pad(ch, choir, h, 56, 52, 70, vel=70, swell=(50, 115))
    arp(ar, saw, h, 56, 64, 86, 1, [0, 1, 2, 3, 2, 1, 2, 3, 4, 3, 2, 1], 78, dur=0.2)
    roots(bs, synb, h, 56, 40, (0, 3, 6, 9), vel=100, dur=0.6)
    for b in range(56):
        for i in (0, 6): dr.note(kit, dr.at(b, i), 0.2, KICK, 112)
        for i in (3, 9): dr.note(kit, dr.at(b, i), 0.2, SNARE, 104)
        for i in range(0, 12, 2): dr.note(kit, dr.at(b, i), 0.05, HAT, 42)
        if b % 4 == 0: dr.note(kit, dr.at(b, 0), 1.5, CRASH, 100)
    return song.mix(wow=0.0006, hiss=0.002)


def titan():
    song = Song(168, 7, 56, 671)
    h = Harmony(0, MINOR, ["Cm", "Ab", "Fm", "G", "Cm", "Eb", "Ab", "G"] * 7, 7)
    bs, mt, dr = song.stem(top=900, crunch=(9, 2)), song.stem(top=3000, gain=0.8), song.stem(top=5500, crunch=(8, 2))
    synb, metal, kit = bs.instrument(SYN_BASS, 110, 64, 30), mt.instrument(METAL_PAD, 90, 64, 80), dr.drums(0, 108, 40)
    pad(mt, metal, h, 56, 48, 63, vel=56, swell=(40, 100))
    roots(bs, synb, h, 56, 36, (0, 2, 4), vel=108, dur=1.6)
    for b in range(56):
        for g in (0, 2, 4): dr.note(kit, dr.at(b, g), 0.3, KICK, 116 if g == 0 else 96)
        dr.note(kit, dr.at(b, 4), 0.2, SNARE, 96)
        if b % 2 == 1:
            for beat, tom in ((5, HI_TOM), (5.5, MID_TOM), (6, LOW_TOM), (6.5, LOW_TOM)): dr.note(kit, dr.at(b, beat), 0.2, tom, 100)
        if b % 8 == 0: dr.note(kit, dr.at(b, 0), 2.0, CRASH, 100)
    return song.mix(wow=0.0008, hiss=0.002)


def gate():
    import score
    return score.gate()


def gate_variant():
    """The Ruined Gate in this manner, to be heard beside the original (not in the mod unless chosen): the same key
    (A-flat lydian) and the same stillness - no beat - a felt piano ringing under the pedal, voices, glints of
    celesta, and deep swells under them."""
    from score import sub
    song = Song(60, 4, 32, 681)
    h = Harmony(8, LYDIAN, ["Abadd9", "Bb", "Cm7", "Fm7", "Abadd9", "Bb", "Gm7", "Ebsus4"] * 4, 4)
    p, vo, gl = song.stem(top=3200), song.stem(top=3500, room=1.0, level=0.9), song.stem(top=6000, gain=0.45, room=1.0)
    piano, voices, cel = p.instrument(PIANO, 100, 60, 110), vo.instrument(CHOIR_PAD, 90, 64, 120), gl.instrument(CELESTA, 90, 70, 120)
    pad(vo, voices, h, 32, 51, 67, vel=54, swell=(20, 90))
    broken(p, piano, h, 32, 32, [(0, 0), (1, 1), (2.5, 2), (3.5, 3)], 42, loose=0.05)
    line = melody(song.rng, h, 24, [[4], [3, 1], [2, 2]], 68, 82, (0, 2), start_bar=8)
    sing(p, piano, h, rests(line, 8), 54)
    for b in range(4, 32, 3): gl.note(cel, gl.at(b, 2), 2.0, notes_in(h.at(b, 2).pcs, 84, 96)[0], 46)
    secs = 32 * 4 + 10
    swells = np.zeros(int(secs * SR))
    for b in range(0, 32, 8):
        f = 440 * 2 ** ((32 + (h.at(b, 0).root - 32) % 12 - 69) / 12)
        x = sub(f, 7.5) * 0.35
        i = int((b * 4 + 1) * SR)
        swells[i:i + len(x)] += x[:len(swells) - i]
    return song.mix(wow=0.0024, hiss=0.004, extra=swells)


TRACKS = {"hub": hub, "hermitage": hermitage, "olympus": olympus, "olympus_peak": olympus_peak, "abyss": abyss, "abyss_trench": abyss_trench,
          "underworld": underworld, "tartarus": tartarus, "passage": passage, "arena": arena, "trial": trial, "summit": summit,
          "wrath": wrath, "titan": titan, "oracle": oracle, "citadel": citadel, "citadel_sea": citadel_sea, "citadel_shade": citadel_shade,
          "gate": gate}
