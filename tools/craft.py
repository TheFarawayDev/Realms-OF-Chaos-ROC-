"""The score in the manner of Minecraft's own music: soft felt piano, warm synth pads, a celesta or music box catching
the light, an electric piano, slow tempos, plenty of room and silence between phrases - and for the fights, synths
and electronic drums rather than an orchestra. Original music: the same clean harmony as before (harmony.py does the
voice-leading and the phrasing), played and paced the way the game's own music is.

Each track keeps a voice of its own:
  hub          felt piano and electric piano, warm pad                     D major     72
  hermitage    music box and piano, a soft pad, birds                      F major     62
  olympus      bright piano and glockenspiel in three, airy pad            G lydian    84  3/4
  olympus_peak synth strings and piano over a soft pulse                   E minor     96
  abyss        vibraphone and electric piano, a watery pad                 B minor     slow 6/8
  abyss_trench a deep pad, low piano notes far apart                       F# minor    50
  underworld   dark piano and choir pad, embers crackling                  C minor     56
  tartarus     a synth bass pulsing under metallic pads, low piano         C# minor    66  5/4
  passage      kalimba and electric piano, a ticking beat                  D minor     92
  arena        square lead, synth bass and 808 drums                       D hijaz    126  9/8
  trial        saw lead, pulsing bass, drums and a choir pad               D minor    140
  summit       choir pad, synth brass, bells, big drums                    B minor    128
  wrath        fast saw arpeggios, drums without a breath                  E harmonic 168  12/8
  titan        heavy synth bass, toms, metallic hits                       C minor    84  7/8
  oracle       celesta and piano over a halo pad                           E dorian    58  3/4
  citadel      synth brass and piano, a soft march                         Bb major    96
  citadel_sea  electric piano rocking in six, a pad like a tide            G major     slow 6/8
  citadel_shade low piano and a bowed pad in three                         F minor     60  3/4
  gate         the synthesised track (score.gate), kept as it was
"""
import random

import numpy as np

from compose import Score, render, finish, SR, CELESTA, GLOCK, MUSICBOX, VIBES, KALIMBA, RHODES, WARM_PAD, HALO
from harmony import (Harmony, voice, pad, bass, arpeggio, melody, play, thirds_below, drum_pitch, notes_in,
                     MAJOR, MINOR, DORIAN, LYDIAN, HIJAZ, HARMONIC)

PIANO, BRIGHT, NEW_AGE, POLY, CHOIR_PAD, BOWED, METAL_PAD, SWEEP = 0, 1, 88, 90, 91, 92, 93, 95
SYN_STRINGS, SYN_BRASS, SQUARE, SAW, SYN_BASS, VOICE_LEAD, RAIN, ECHOES, BIRDS = 50, 62, 80, 81, 39, 85, 96, 102, 123
KICK, SNARE, CLAP, HAT, OPEN_HAT, CRASH, LOW_TOM, MID_TOM, HI_TOM, RIDE = 36, 38, 39, 42, 46, 49, 45, 47, 50, 51
KIT_808 = 25


def rests(notes, every=2, phrase=4, keep=1):
    """Silence between phrases: of every `every` phrases of `phrase` bars, only `keep` are sung."""
    return [n for n in notes if ((n[0] // phrase) % every) < keep]


def soft(x, top=9000):
    """A touch of warmth over the whole: gentle saturation and a softened top, as the game's music has."""
    from score import lowpass
    y = np.tanh(x * 1.3) / np.tanh(1.3)
    return lowpass(y, top)


def piano_hands(s, ch, h, bars, lo_root, hi, step, order, vel=58, start_bar=0, dur=None):
    """A pianist's two hands: the left the chord's root held, the right breaking the chord gently."""
    bass(s, ch, h, bars, lo_root, pattern=((0, h.per_bar - 0.1, 0),), vel=vel - 6, start_bar=start_bar)
    arpeggio(s, ch, h, bars, lo_root + 12, hi, step, order, vel=vel, accent=4, start_bar=start_bar, dur=dur)


def done(s, room, level, warm=True, gain=0.5):
    x = render(s, room=room, damp=0.35, level=level, gain=gain)
    return finish(soft(x) if warm else soft(x, 6500), 6.0)


# ------------------------------------------------------------------------------------------------ calm places
def hub():
    rng = random.Random(301)
    h = Harmony(2, MAJOR, ["D", "Bm", "G", "A", "D", "F#m", "G", "A"] * 5, 4)
    s = Score(72, 4, 40)
    piano, ep, padc = s.instrument(PIANO, 96, 52, 90), s.instrument(RHODES, 78, 80, 90), s.instrument(WARM_PAD, 66, 64, 100)
    pad(s, padc, h, 38, 57, 69, vel=46, swell=(30, 85))
    piano_hands(s, piano, h, 40, 38, 74, 1.0, [0, 2, 1, 3], vel=56)
    play(s, piano, rests(melody(rng, h, 24, [[2, 2], [3, 1], [4], [1.5, 0.5, 2]], 69, 83, (0, 2), start_bar=8)), 72)
    play(s, ep, rests(melody(random.Random(302), h, 16, [[4], [2, 2], [3, 1]], 62, 76, (0, 2), start_bar=20), keep=1, every=2), 60)
    return done(s, 0.85, 0.75)


def hermitage():
    rng = random.Random(311)
    h = Harmony(5, MAJOR, ["F", "Am", "Bb", "F", "Dm", "Bb", "C", "F"] * 4, 4)
    s = Score(62, 4, 32)
    box, piano, padc, birds = s.instrument(MUSICBOX, 92, 44, 90), s.instrument(PIANO, 86, 70, 90), s.instrument(NEW_AGE, 60, 64, 100), s.instrument(BIRDS, 30, 100, 90)
    pad(s, padc, h, 32, 53, 69, vel=44, swell=(25, 80))
    piano_hands(s, piano, h, 32, 41, 72, 2.0, [0, 1, 2, 1], vel=50, dur=3.0)
    play(s, box, rests(melody(rng, h, 24, [[1, 1, 2], [2, 1, 1], [1.5, 0.5, 2], [4]], 72, 88, (0, 2), start_bar=4), every=2, keep=1), 74)
    for b in range(2, 32, 6): s.note(birds, s.at(b, rng.uniform(0, 3)), 1.0, 77, 40)
    return done(s, 0.9, 0.8)


def olympus():
    rng = random.Random(321)
    h = Harmony(7, LYDIAN, ["G", "A", "Em", "G", "C", "D", "Em", "D", "G", "Bm", "C", "A", "Em", "C", "D", "G"] * 3, 3)
    s = Score(84, 3, 48)
    piano, glock, padc = s.instrument(BRIGHT, 94, 50, 85), s.instrument(GLOCK, 66, 90, 95), s.instrument(HALO, 62, 64, 105)
    pad(s, padc, h, 46, 55, 71, vel=44, swell=(25, 80))
    bass(s, piano, h, 46, 43, pattern=((0, 2.8, 0),), vel=56)
    arpeggio(s, piano, h, 46, 62, 86, 1.0, [0, 1, 2], vel=50, start_bar=0, dur=1.5)
    line = melody(rng, h, 32, [[2, 1], [3], [1, 1, 1], [1.5, 0.5, 1]], 74, 91, (0,), start_bar=8, cadence=[3])
    play(s, piano, rests(line), 74)
    play(s, glock, [n for n in rests(line, every=2, keep=1) if n[1] == 0], 52)
    return done(s, 0.8, 0.7)


def abyss():
    rng = random.Random(341)
    h = Harmony(11, MINOR, ["Bm", "G", "D", "A", "Bm", "Em", "G", "F#"] * 5, 6)
    s = Score(126, 6, 40)
    vib, ep, padc = s.instrument(VIBES, 86, 44, 100), s.instrument(RHODES, 76, 80, 100), s.instrument(SWEEP, 60, 64, 110)
    pad(s, padc, h, 38, 54, 69, vel=46, swell=(25, 80))
    arpeggio(s, vib, h, 40, 59, 83, 1.0, [0, 2, 4, 2, 1, 2], vel=50, dur=2.0)
    bass(s, ep, h, 38, 35, pattern=((0, 5.8, 0),), vel=60)
    play(s, ep, rests(melody(rng, h, 24, [[3, 3], [6], [2, 1, 3]], 64, 78, (0, 3), start_bar=8, cadence=[6])), 66)
    return done(s, 0.95, 0.85)


def abyss_trench():
    rng = random.Random(351)
    h = Harmony(6, MINOR, ["F#m", "D", "Bm", "C#", "F#m", "A", "D", "C#"] * 3, 4)
    s = Score(50, 4, 24)
    deep, piano, echo = s.instrument(BOWED, 74, 64, 115), s.instrument(PIANO, 84, 56, 110), s.instrument(ECHOES, 46, 80, 120)
    pad(s, deep, h, 24, 42, 60, vel=56, swell=(30, 95))
    for b in range(24):
        chord = h.at(b, 0)
        if b % 2 == 0: s.note(piano, s.at(b, rng.choice([0, 1, 2])), 3.0, 30 + (chord.root - 30) % 12, 50)
        if b % 3 == 1: s.note(piano, s.at(b, 2), 2.0, notes_in(chord.pcs, 60, 72)[rng.randrange(2)], 44)
    for b in (4, 12, 20): s.note(echo, s.at(b), 6.0, 66, 40)
    return done(s, 1.0, 0.9)


def underworld():
    rng = random.Random(361)
    h = Harmony(0, MINOR, ["Cm", "Fm", "G", "Cm", "Ab", "Fm", "G", "Cm"] * 4, 4)
    s = Score(56, 4, 32)
    piano, choir, padc = s.instrument(PIANO, 90, 56, 95), s.instrument(CHOIR_PAD, 66, 64, 105), s.instrument(METAL_PAD, 50, 64, 110)
    pad(s, choir, h, 32, 48, 63, vel=50, swell=(25, 85))
    pad(s, padc, h, 24, 60, 72, vel=34, swell=(15, 60), start_bar=8)
    piano_hands(s, piano, h, 32, 36, 64, 2.0, [0, 1, 2, 1], vel=52, dur=2.5)
    play(s, piano, rests(melody(rng, h, 24, [[2, 2], [3, 1], [4]], 60, 74, (0, 2), start_bar=8)), 68)
    x = done(s, 0.9, 0.8)
    from sfx_more import crackle
    x[:len(x)] += crackle(len(x) / SR, 4, 7, 2500)[:len(x)] * 0.08          # embers, now and then
    return finish(x, 6.0)


def tartarus():
    rng = random.Random(371)
    h = Harmony(1, MINOR, ["C#m", "A", "F#m", "G#", "C#m", "E", "A", "G#"] * 4, 5)
    s = Score(66, 5, 30)
    synb, metal, piano = s.instrument(SYN_BASS, 74, 64, 70), s.instrument(METAL_PAD, 74, 64, 110), s.instrument(PIANO, 92, 56, 100)
    pad(s, metal, h, 30, 52, 66, vel=48, swell=(25, 90))
    for b in range(2, 30):
        root = 25 + (h.at(b, 0).root - 25) % 12
        for beat, v in ((0, 96), (1.5, 70), (3, 86), (4, 64)): s.note(synb, s.at(b, beat), 0.6, root, v)
    play(s, piano, rests(melody(rng, h, 24, [[3, 2], [5], [2, 3]], 49, 63, (0, 3), start_bar=4, cadence=[5])), 66)
    return done(s, 0.95, 0.8)


def passage():
    rng = random.Random(381)
    h = Harmony(2, MINOR, ["Dm", "Gm", "C", "F", "Bb", "Gm", "A", "Dm"] * 5, 4)
    s = Score(92, 4, 40)
    kal, ep, padc, dr = s.instrument(KALIMBA, 92, 44, 70), s.instrument(RHODES, 80, 80, 80), s.instrument(WARM_PAD, 54, 64, 95), s.drums(0, 60, 40)
    pad(s, padc, h, 38, 57, 69, vel=40, swell=(25, 70))
    arpeggio(s, kal, h, 40, 62, 86, 0.5, [0, 2, 1, 3, 2, 4, 3, 1], vel=58)
    bass(s, ep, h, 38, 38, pattern=((0, 1.9, 0), (2, 1.9, 7)), vel=56, start_bar=2)
    play(s, ep, rests(melody(rng, h, 24, [[1, 1, 2], [2, 2], [1.5, 0.5, 2]], 65, 79, (0, 2), start_bar=8)), 70)
    for b in range(4, 38):
        for i in range(4): s.note(dr, s.at(b, i + 0.5), 0.05, HAT, 34)
        s.note(dr, s.at(b, 0), 0.2, KICK, 46)
    return done(s, 0.7, 0.6)


def oracle():
    rng = random.Random(391)
    h = Harmony(4, DORIAN, ["Em", "D", "C", "Em", "Am", "Em", "D", "B"] * 5, 3)
    s = Score(58, 3, 40)
    cel, piano, halo = s.instrument(CELESTA, 84, 44, 105), s.instrument(PIANO, 84, 76, 100), s.instrument(HALO, 66, 64, 115)
    pad(s, halo, h, 38, 52, 67, vel=46, swell=(25, 80))
    bass(s, piano, h, 38, 40, pattern=((0, 2.8, 0),), vel=50)
    arpeggio(s, cel, h, 40, 64, 88, 1.0, [0, 2, 1], vel=48, dur=1.8)
    play(s, piano, rests(melody(rng, h, 24, [[2, 1], [3], [1, 2]], 64, 79, (0,), start_bar=8, cadence=[3])), 64)
    return done(s, 1.0, 0.9)


def citadel_sea():
    rng = random.Random(401)
    h = Harmony(7, MAJOR, ["G", "C", "D", "G", "Em", "C", "D", "D"] * 6, 6)
    s = Score(132, 6, 48)
    ep, padc, piano = s.instrument(RHODES, 86, 50, 90), s.instrument(SWEEP, 56, 64, 105), s.instrument(PIANO, 80, 76, 90)
    pad(s, padc, h, 46, 55, 69, vel=42, swell=(25, 80))
    arpeggio(s, ep, h, 48, 55, 79, 1.0, [0, 1, 2, 3, 2, 1], vel=52, dur=1.5)
    bass(s, ep, h, 46, 31, pattern=((0, 2.8, 0), (3, 2.8, 7)), vel=56)
    play(s, piano, rests(melody(rng, h, 32, [[3, 3], [2, 1, 3], [6]], 67, 81, (0, 3), start_bar=8, cadence=[6])), 66)
    return done(s, 0.85, 0.75)


def citadel_shade():
    rng = random.Random(411)
    h = Harmony(5, MINOR, ["Fm", "Db", "Bbm", "C", "Fm", "Ab", "Bbm", "C"] * 5, 3)
    s = Score(60, 3, 40)
    piano, bowed = s.instrument(PIANO, 90, 56, 100), s.instrument(BOWED, 66, 64, 110)
    pad(s, bowed, h, 38, 48, 63, vel=50, swell=(25, 85))
    bass(s, piano, h, 38, 29, pattern=((0, 2.8, 0),), vel=56)
    arpeggio(s, piano, h, 38, 53, 70, 1.0, [0, 2, 1], vel=44, start_bar=2, dur=1.5)
    play(s, piano, rests(melody(rng, h, 24, [[2, 1], [3], [1, 1, 1]], 53, 67, (0,), start_bar=8, cadence=[3])), 64)
    return done(s, 0.9, 0.8)


# ------------------------------------------------------------------------------------------------ marches and fights
def olympus_peak():
    rng = random.Random(331)
    h = Harmony(4, MINOR, ["Em", "C", "G", "D", "Em", "C", "Am", "B"] * 5, 4)
    s = Score(96, 4, 40)
    st, piano, dr = s.instrument(SYN_STRINGS, 80, 64, 85), s.instrument(PIANO, 90, 60, 80), s.drums(0, 66, 40)
    pad(s, st, h, 40, 52, 69, vel=56, swell=(40, 100))
    arpeggio(s, piano, h, 40, 55, 79, 0.5, [0, 1, 2, 3, 2, 1, 2, 1], vel=52, start_bar=4)
    bass(s, piano, h, 36, 40, pattern=((0, 1.9, 0), (2, 1.9, 0)), vel=58, start_bar=4)
    play(s, piano, rests(melody(rng, h, 24, [[2, 1, 1], [1.5, 0.5, 2], [4]], 67, 81, (0, 2), start_bar=12)), 78)
    for b in range(8, 38):
        s.note(dr, s.at(b, 0), 0.2, KICK, 64)
        s.note(dr, s.at(b, 2), 0.2, KICK, 56)
        s.note(dr, s.at(b, 1), 0.15, SNARE, 40)
        s.note(dr, s.at(b, 3), 0.15, SNARE, 46)
    return done(s, 0.75, 0.6)


def citadel():
    rng = random.Random(421)
    h = Harmony(10, MAJOR, ["Bb", "Eb", "F", "Bb", "Gm", "Eb", "F", "F"] * 5, 4)
    s = Score(96, 4, 40)
    brass, piano, dr = s.instrument(SYN_BRASS, 84, 50, 70), s.instrument(PIANO, 86, 76, 70), s.drums(0, 76, 40)
    pad(s, brass, h, 36, 53, 67, vel=56, swell=(40, 100), start_bar=4, size=3)
    bass(s, piano, h, 40, 34, pattern=((0, 0.9, 0), (2, 0.9, 7)), vel=62)
    play(s, piano, rests(melody(rng, h, 32, [[1.5, 0.5, 1, 1], [1, 1, 2], [2, 1, 1]], 65, 79, (0, 2), start_bar=4), every=3, keep=2), 80)
    for b in range(4, 38):
        for beat, v in ((0, 70), (1, 44), (2, 62), (3, 44), (3.5, 36)): s.note(dr, s.at(b, beat), 0.1, SNARE, v)
        s.note(dr, s.at(b, 0), 0.2, KICK, 70)
    return done(s, 0.7, 0.6)


def arena():
    rng = random.Random(431)
    h = Harmony(2, HIJAZ, ["D", "Eb", "D", "Gm", "D", "Eb", "Cm", "D"] * 8, 9)
    s = Score(252, 9, 64)
    lead, bassc, padc, dr = s.instrument(SQUARE, 72, 50, 60), s.instrument(SYN_BASS, 96, 64, 30), s.instrument(POLY, 56, 64, 70), s.drums(KIT_808, 100, 30)
    groups = (0, 2, 4, 6)
    pad(s, padc, h, 62, 55, 69, vel=44, swell=None)
    for b in range(64):
        chord = h.at(b, 0)
        root = 38 + (chord.root - 38) % 12
        for g in groups:
            s.note(bassc, s.at(b, g), 0.8, root, 96 if g == 0 else 78)
            s.note(dr, s.at(b, g), 0.2, KICK if g in (0, 4) else CLAP, 96 if g == 0 else 72)
        for i in range(9): s.note(dr, s.at(b, i + 0.5), 0.05, HAT, 40)
    play(s, lead, melody(rng, h, 16, [[2, 2, 2, 3], [1, 1, 2, 2, 3], [4, 2, 3]], 62, 76, groups, start_bar=8, cadence=[9]), 86)
    play(s, lead, melody(random.Random(432), h, 16, [[2, 2, 2, 3], [3, 3, 3]], 64, 79, groups, start_bar=32, cadence=[9]), 90)
    return done(s, 0.5, 0.4, warm=False)


def trial():
    rng = random.Random(441)
    prog = ["Dm", "Bb", "F", "C", "Dm", "Bb", "Gm", "A", "Dm", "F", "C", "Bb", "Gm", "Bb", "A", "A"]
    h = Harmony(2, MINOR, prog * 4, 4)
    s = Score(140, 4, 64)
    lead, arp, bassc, choir, dr = s.instrument(SAW, 86, 50, 50), s.instrument(SQUARE, 70, 80, 50), s.instrument(SYN_BASS, 100, 64, 30), s.instrument(CHOIR_PAD, 76, 64, 70), s.drums(0, 100, 30)
    for b in range(64):
        chord = h.at(b, 0)
        root = 38 + (chord.root - 38) % 12
        intro = b < 8
        for i in range(8): s.note(bassc, s.at(b, i * 0.5), 0.4, root + (12 if i % 4 == 3 else 0), 100 if i % 2 == 0 else 80)
        tones = notes_in(chord.pcs, 62, 81)
        if not intro:
            for i in range(16): s.note(arp, s.at(b, i / 4), 0.2, tones[[0, 1, 2, 1, 2, 3, 2, 1][i % 8] % len(tones)], 70 if i % 4 else 84)
            for beat in (0, 1.5, 2, 3.25): s.note(dr, s.at(b, beat), 0.2, KICK, 104)
            for beat in (1, 3): s.note(dr, s.at(b, beat), 0.2, SNARE, 96)
            for i in range(8): s.note(dr, s.at(b, i * 0.5), 0.05, HAT, 56)
            if b % 4 == 0: s.note(dr, s.at(b, 0), 1.5, CRASH, 96)
    pad(s, choir, h, 48, 55, 72, vel=60, swell=(40, 110), start_bar=16)
    theme = melody(rng, h, 16, [[1.5, 0.5, 2], [1, 1, 2], [3, 1]], 62, 77, (0, 2), start_bar=24)
    play(s, lead, theme, 96)
    play(s, lead, [(b + 16, beat, dur, m + 12 if m < 70 else m) for b, beat, dur, m in theme if b + 16 < 64], 100)
    return done(s, 0.5, 0.4, warm=False, gain=0.45)


def summit():
    rng = random.Random(451)
    prog = ["Bm", "G", "D", "A", "Bm", "Em", "F#", "F#", "G", "D", "A", "Bm", "G", "A", "F#", "Bm"]
    h = Harmony(11, MINOR, prog * 4, 4)
    s = Score(128, 4, 64)
    choir, brass, bells, bassc, dr = s.instrument(CHOIR_PAD, 88, 64, 75), s.instrument(SYN_BRASS, 90, 50, 60), s.instrument(GLOCK, 70, 90, 80), s.instrument(SYN_BASS, 96, 64, 30), s.drums(0, 100, 40)
    pad(s, choir, h, 64, 54, 73, vel=76, swell=(60, 120))
    for b in range(64):
        chord = h.at(b, 0)
        root = 35 + (chord.root - 35) % 12
        for beat in (0, 1, 2, 3): s.note(bassc, s.at(b, beat), 0.8, root, 96 if beat == 0 else 80)
        if b >= 8:
            for beat in (0, 2.5): s.note(dr, s.at(b, beat), 0.2, KICK, 110)
            for beat in (1, 3): s.note(dr, s.at(b, beat), 0.2, SNARE, 100)
            for beat, tom in ((3.25, HI_TOM), (3.5, MID_TOM), (3.75, LOW_TOM)):
                if b % 4 == 3: s.note(dr, s.at(b, beat), 0.15, tom, 96)
            if b % 4 == 0: s.note(dr, s.at(b, 0), 1.5, CRASH, 104)
        if b % 2 == 0: s.note(bells, s.at(b, 0), 2.0, 71 + (chord.root - 71) % 12, 64)
    chant = melody(rng, h, 16, [[2, 2], [1, 1, 2], [3, 1]], 62, 76, (0, 2), start_bar=16)
    play(s, brass, chant, 96)
    play(s, brass, [(b + 32, beat, dur, m) for b, beat, dur, m in chant if b + 32 < 64], 100)
    return done(s, 0.6, 0.5, warm=False, gain=0.45)


def wrath():
    rng = random.Random(461)
    h = Harmony(4, HARMONIC, ["Em", "C", "Am", "B", "Em", "G", "C", "B"] * 7, 12)
    s = Score(504, 12, 56)
    saw, bassc, choir, dr = s.instrument(SAW, 70, 60, 55), s.instrument(SYN_BASS, 100, 64, 30), s.instrument(CHOIR_PAD, 80, 64, 65), s.drums(KIT_808, 105, 30)
    pad(s, choir, h, 56, 52, 70, vel=70, swell=(50, 115))
    for b in range(56):
        chord = h.at(b, 0)
        root = 40 + (chord.root - 40) % 12
        tones = notes_in(chord.pcs, 64, 86)
        for i in range(12):
            s.note(saw, s.at(b, i), 0.2, tones[[0, 1, 2, 3, 2, 1, 2, 3, 4, 3, 2, 1][i] % len(tones)], 78 if i % 3 else 92)
            if i % 3 == 0: s.note(bassc, s.at(b, i), 0.6, root, 100)
        for i in (0, 6): s.note(dr, s.at(b, i), 0.2, KICK, 112)
        for i in (3, 9): s.note(dr, s.at(b, i), 0.2, SNARE, 104)
        for i in range(0, 12, 2): s.note(dr, s.at(b, i), 0.05, HAT, 40)
        if b % 4 == 0: s.note(dr, s.at(b, 0), 1.5, CRASH, 110)
    return done(s, 0.45, 0.35, warm=False, gain=0.42)


def titan():
    rng = random.Random(471)
    h = Harmony(0, MINOR, ["Cm", "Ab", "Fm", "G", "Cm", "Eb", "Ab", "G"] * 7, 7)
    s = Score(168, 7, 56)
    bassc, metal, dr = s.instrument(SYN_BASS, 106, 64, 30), s.instrument(METAL_PAD, 70, 64, 80), s.drums(0, 108, 40)
    pad(s, metal, h, 56, 48, 63, vel=56, swell=(40, 100))
    for b in range(56):
        chord = h.at(b, 0)
        root = 36 + (chord.root - 36) % 12
        for g in (0, 2, 4):
            s.note(bassc, s.at(b, g), 1.6 if g < 4 else 2.6, root, 108 if g == 0 else 90)
            s.note(dr, s.at(b, g), 0.3, KICK, 116 if g == 0 else 96)
        s.note(dr, s.at(b, 4), 0.2, SNARE, 96)
        if b % 2 == 1:
            for beat, tom in ((5, HI_TOM), (5.5, MID_TOM), (6, LOW_TOM), (6.5, LOW_TOM)): s.note(dr, s.at(b, beat), 0.2, tom, 100)
        if b % 8 == 0: s.note(dr, s.at(b, 0), 2.0, CRASH, 110)
    return done(s, 0.55, 0.45, warm=False, gain=0.45)


def gate():
    import score
    return score.gate()


TRACKS = {"hub": hub, "hermitage": hermitage, "olympus": olympus, "olympus_peak": olympus_peak, "abyss": abyss, "abyss_trench": abyss_trench,
          "underworld": underworld, "tartarus": tartarus, "passage": passage, "arena": arena, "trial": trial, "summit": summit,
          "wrath": wrath, "titan": titan, "oracle": oracle, "citadel": citadel, "citadel_sea": citadel_sea, "citadel_shade": citadel_shade,
          "gate": gate}
