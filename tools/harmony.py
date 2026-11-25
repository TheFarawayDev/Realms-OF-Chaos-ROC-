"""The mod's score, composed around its harmony so that everything sounding at once belongs together.

How a track is made:
  * a progression of chords, written as chord symbols, one or two to a bar, all in one key;
  * pads and sustained parts voice-led: each chord is voiced as near as it can be to the last, so the inner voices
    move by step and nothing jumps; every sustained note stops at the chord change instead of ringing into the next;
  * the bass on the chord's root (now and then its fifth), low;
  * melodies built in phrases: a two-bar motif, repeated and answered, its notes on the strong beats always tones of
    the chord under them, the notes between stepping through the key from one to the next; a phrase rises and falls,
    and ends on a chord tone - the last one on the key's own note;
  * accompaniment figures (arpeggios, strums, ostinati) drawn only from the chord sounding.

Every track is played by instruments of an ancient, mythic world - harps and lyre-like guitars, pan flutes and
reeds, an orchestra's strings, horns and drums, choirs - and has its own key, tempo, metre and hall. The Ruined Gate's
track is the earlier synthesised one (score.gate), kept as it was.
"""
import random

import numpy as np

from compose import (Score, render, finish, SR, HARP, NYLON, FLUTE, STRINGS, SLOW_STRINGS, CHOIR, OOHS, HORN, BRASS, TRUMPET,
                     TROMBONE, TUBA, TIMPANI, TAIKO, PIZZ, CELLO, CONTRABASS, CELESTA, GLOCK, TUBULAR, ORCH_HIT, TREMOLO, WARM_PAD,
                     BIRDS, SEASHORE, CRASH, SNARE, TRIANGLE, LO_CONGA, MUTE_CONGA, HI_BONGO, TAMB, HI_WOOD, LO_WOOD, KICK, ORCH_KIT)

PAN_FLUTE, OBOE, CLARINET, BASSOON, ENGLISH_HORN, VIOLA, VIOLIN, SYNTH_CHOIR, BOWED, MELODIC_TOM = 75, 68, 71, 70, 69, 41, 40, 54, 92, 117
SPLASH, RIDE_BELL, CHINA, LOW_TOM, MID_TOM, HIGH_TOM, FLOOR_TOM = 55, 53, 52, 45, 47, 50, 41

NOTE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
QUALITY = {"": [0, 4, 7], "m": [0, 3, 7], "7": [0, 4, 7, 10], "m7": [0, 3, 7, 10], "maj7": [0, 4, 7, 11], "sus4": [0, 5, 7],
           "sus2": [0, 2, 7], "add9": [0, 4, 7, 14], "madd9": [0, 3, 7, 14], "dim": [0, 3, 6], "5": [0, 7]}
MAJOR = [0, 2, 4, 5, 7, 9, 11]
MINOR = [0, 2, 3, 5, 7, 8, 10]
DORIAN = [0, 2, 3, 5, 7, 9, 10]
LYDIAN = [0, 2, 4, 6, 7, 9, 11]


class Chord:
    def __init__(self, symbol):
        r = symbol[0]
        rest = symbol[1:]
        pc = NOTE[r]
        if rest.startswith("b"): pc -= 1; rest = rest[1:]
        elif rest.startswith("#"): pc += 1; rest = rest[1:]
        self.root = pc % 12
        self.tones = QUALITY[rest]
        self.pcs = sorted({(self.root + t) % 12 for t in self.tones})
        self.symbol = symbol

    def has(self, midi):
        return midi % 12 in self.pcs


class Harmony:
    """A key, and its chords over time: bars of one or two chords each."""

    def __init__(self, tonic, scale, progression, per_bar):
        self.tonic, self.scale, self.per_bar = tonic % 12, scale, per_bar
        self.bars = [[Chord(c) for c in (bar if isinstance(bar, list) else [bar])] for bar in progression]

    def at(self, bar, beat):
        chords = self.bars[bar % len(self.bars)]
        return chords[min(len(chords) - 1, int(beat / (self.per_bar / len(chords))))]

    def span(self, bar, beat):
        """Where the chord sounding at this moment began and how long it lasts, in beats from the bar's start."""
        chords = self.bars[bar % len(self.bars)]
        size = self.per_bar / len(chords)
        i = min(len(chords) - 1, int(beat / size))
        return i * size, size

    def scale_pcs(self, chord):
        """The key's notes, bent to fit the chord (a raised seventh under a major dominant in a minor key)."""
        pcs = [(self.tonic + s) % 12 for s in self.scale]
        for c in chord.pcs:
            if c not in pcs:
                pcs = [c if (p - c) % 12 in (1, 11) else p for p in pcs]
        return sorted(set(pcs))


def notes_in(pcs, lo, hi):
    return [m for m in range(lo, hi + 1) if m % 12 in pcs]


def voice(chord, prev, lo, hi, size=None):
    """The chord voiced in close position within lo..hi, as near as it can be to the voicing before it."""
    tones = sorted({(chord.root + t) for t in chord.tones})
    size = size or min(4, len(tones))
    best, cost = None, 1e9
    base = [t % 12 for t in tones]
    for octave_root in range(lo - 12, hi + 1):
        if octave_root % 12 != chord.root: continue
        for inv in range(len(base)):
            stack = []
            pcs = base[inv:] + base[:inv]
            m = octave_root
            for pc in pcs:
                while m % 12 != pc: m += 1
                stack.append(m)
            stack = stack[:size] if len(stack) >= size else stack + [stack[0] + 12]
            if stack[0] < lo or stack[-1] > hi: continue
            c = sum(abs(a - b) for a, b in zip(stack, prev)) if prev else abs(sum(stack) / len(stack) - (lo + hi) / 2)
            if c < cost: best, cost = stack, c
    return best or [lo + (chord.root - lo) % 12 + t for t in chord.tones[:size]]


def pad(s, ch, h, bars, lo, hi, vel=64, swell=(50, 110), size=None, start_bar=0, every=1):
    """Sustained chords, voice-led, each stopping at the next change; swelling and falling with each."""
    prev = None
    for b in range(start_bar, start_bar + bars):
        for i, chord in enumerate(h.bars[b % len(h.bars)]):
            size_beats = h.per_bar / len(h.bars[b % len(h.bars)])
            if (b - start_bar) % every: continue
            v = voice(chord, prev, lo, hi, size)
            prev = v
            t = s.at(b, i * size_beats)
            for m in v: s.note(ch, t, size_beats * every - 0.05, m, vel)
            if swell: s.swell(ch, t, size_beats * every, swell[0], swell[1], steps=8)


def bass(s, ch, h, bars, lo, pattern=((0, 1.0, 0),), vel=80, start_bar=0):
    """The root (pattern offsets: 0 root, 7 fifth, 12 octave) on the given beats of each chord."""
    for b in range(start_bar, start_bar + bars):
        for beat, dur, iv in pattern:
            chord = h.at(b, beat)
            root = lo + (chord.root - lo) % 12
            m = root + iv
            if iv == 7 and not chord.has(m): m = root
            s.note(ch, s.at(b, beat), dur, m, vel)


def arpeggio(s, ch, h, bars, lo, hi, step, order, vel=60, accent=10, start_bar=0, dur=None):
    """A figure through the notes of the chord sounding, in the given order of chord-tone indices (wrapping upward)."""
    for b in range(start_bar, start_bar + bars):
        n = int(round(h.per_bar / step))
        for i in range(n):
            beat = i * step
            chord = h.at(b, beat)
            tones = notes_in(chord.pcs, lo, hi)
            if not tones: continue
            idx = order[i % len(order)]
            m = tones[min(len(tones) - 1, idx)]
            begun, size = h.span(b, beat)
            d = min(dur or step * 1.8, begun + size - beat - 0.02)
            s.note(ch, s.at(b, beat), max(0.05, d), m, vel + (accent if i % len(order) == 0 else 0))


def melody(rng, h, bars, rhythms, lo, hi, strong, start_bar=0, motif_bars=2, phrase_bars=4, cadence=None, end_on_tonic=True, arch=5):
    """A melody in phrases over the harmony. rhythms: per-bar lists of durations; strong: beats that must be chord
    tones. Returns (bar, beat, dur, midi) notes."""
    motif = [rng.choice(rhythms) for _ in range(motif_bars)]
    answer = [rng.choice(rhythms) for _ in range(motif_bars)]
    out = []
    centre = (lo + hi) // 2
    prev = centre
    for b in range(start_bar, start_bar + bars):
        k = (b - start_bar) % phrase_bars
        pos = k / max(1, phrase_bars - 1)
        last_bar = k == phrase_bars - 1
        pattern = (cadence or [h.per_bar]) if last_bar else (motif if (b - start_bar) % (2 * phrase_bars) < phrase_bars else answer)[k % motif_bars]
        beat = 0.0
        target = centre + arch * np.sin(np.pi * (pos * 0.9 + 0.05)) - 2
        for j, dur in enumerate(pattern):
            chord = h.at(b, beat)
            pcs = h.scale_pcs(chord)
            on_strong = any(abs(beat - sb) < 1e-6 for sb in strong) or (last_bar and j == len(pattern) - 1)
            if on_strong:
                cands = [m for m in notes_in(chord.pcs, lo, hi) if abs(m - prev) <= 7] or notes_in(chord.pcs, lo, hi)
                if last_bar and j == len(pattern) - 1:
                    final = end_on_tonic and b == start_bar + bars - 1
                    want = [m for m in cands if m % 12 == (h.tonic if final else chord.root)] or cands
                    m = min(want, key=lambda x: abs(x - prev) + 0.3 * abs(x - target))
                else:
                    m = min(cands, key=lambda x: abs(x - target) * 0.7 + abs(x - prev) * 0.5 + rng.random())
            else:
                scale = notes_in(pcs, lo, hi)
                direction = 1 if target > prev else -1
                if rng.random() < 0.25: direction = -direction
                ups = [x for x in scale if (x - prev) * direction > 0]
                m = min(ups, key=lambda x: abs(x - prev)) if ups else prev
            out.append((b, beat, dur, m))
            prev = m
            beat += dur
    return out


def drum_pitch(chord, lo=43):
    """A pitched drum (taiko, toms - they have a pitch in General MIDI) tuned to the chord's root, so it never rubs."""
    return lo + (chord.root - lo) % 12


def play(s, ch, notes, vel=88, legato=0.96, h=None, shape=True):
    for i, (b, beat, dur, m) in enumerate(notes):
        v = vel + (6 if beat == 0 else 0) - (4 if dur < 0.75 else 0)
        s.note(ch, s.at(b, beat), dur * legato, m, v)


def thirds_below(h, notes):
    """A second voice a third (or a sixth's inversion) under the melody, always in the chord's key notes."""
    out = []
    for b, beat, dur, m in notes:
        chord = h.at(b, beat)
        pcs = h.scale_pcs(chord)
        cands = [x for x in range(m - 5, m - 2) if x % 12 in pcs]
        tones = [x for x in cands if chord.has(x)]
        out.append((b, beat, dur, (tones or cands or [m - 12])[-1]))
    return out


# ------------------------------------------------------------------------------------------------ the tracks
def hub():
    """The hub, the gods' plaza: a harp and a lyre-like guitar under a flute, strings softly behind, a frame drum."""
    rng = random.Random(101)
    prog = ["D", "Bm", "G", "A", "D", "F#m", "G", ["Em", "A"]] * 6
    h = Harmony(2, MAJOR, prog, 4)
    s = Score(80, 4, 44)
    harp, gtr, flute, strings, cello, dr = (s.instrument(HARP, 92, 40, 60), s.instrument(NYLON, 86, 84, 55), s.instrument(FLUTE, 100, 64, 60),
                                            s.instrument(SLOW_STRINGS, 72, 64, 75), s.instrument(CELLO, 90, 70, 55), s.drums(0, 70, 40))
    pad(s, strings, h, 40, 57, 72, vel=56, swell=(40, 95), start_bar=2)
    bass(s, cello, h, 40, 38, pattern=((0, 1.9, 0), (2, 1.9, 7)), vel=74, start_bar=2)
    arpeggio(s, harp, h, 44, 50, 81, 0.5, [0, 1, 2, 3, 4, 3, 2, 1], vel=58)
    arpeggio(s, gtr, h, 32, 55, 74, 1.0, [0, 1, 2, 1], vel=50, start_bar=6)
    line = melody(rng, h, 16, [[1.5, 0.5, 2], [1, 1, 2], [2, 1, 1], [1, 0.5, 0.5, 2]], 69, 86, (0, 2), start_bar=10)
    play(s, flute, line, 90)
    line2 = melody(random.Random(102), h, 16, [[2, 2], [1.5, 0.5, 1, 1], [3, 1]], 66, 83, (0, 2), start_bar=26)
    play(s, flute, line2, 86)
    for b in range(10, 42):
        s.note(dr, s.at(b, 0), 0.4, LO_CONGA, 62)
        s.note(dr, s.at(b, 2), 0.3, MUTE_CONGA, 48)
        s.note(dr, s.at(b, 3.5), 0.2, HI_BONGO, 36)
    s.chord(harp, s.at(43), 3.8, [50, 57, 62, 66, 69], 70, spread=0.08)
    return finish(render(s, room=0.6, damp=0.4, level=0.55))


def hermitage():
    """Your own island: a pan flute's shepherd song over a harp, a cello and soft strings, birds in the garden."""
    rng = random.Random(111)
    prog = ["F", "Am", "Bb", "F", "Dm", "Bb", "C", "F"] * 5
    h = Harmony(5, MAJOR, prog, 4)
    s = Score(66, 4, 36)
    pan, harp, strings, cello, birds = (s.instrument(PAN_FLUTE, 100, 64, 75), s.instrument(HARP, 90, 44, 70), s.instrument(SLOW_STRINGS, 68, 70, 80),
                                        s.instrument(CELLO, 80, 80, 70), s.instrument(BIRDS, 34, 100, 90))
    pad(s, strings, h, 34, 53, 69, vel=52, swell=(35, 90))
    bass(s, cello, h, 34, 41, pattern=((0, 3.9, 0),), vel=66)
    arpeggio(s, harp, h, 36, 53, 77, 1.0, [0, 2, 1, 3], vel=56, dur=2.0)
    play(s, pan, melody(rng, h, 12, [[2, 1, 1], [1.5, 0.5, 2], [3, 1], [1, 1, 2]], 69, 84, (0, 2), start_bar=4), 88)
    play(s, pan, melody(random.Random(112), h, 12, [[2, 2], [1, 1, 1, 1], [3, 1]], 67, 82, (0, 2), start_bar=20), 84)
    for b in range(0, 36, 5): s.note(birds, s.at(b, rng.uniform(0, 3)), 1.2, 74 + rng.randrange(5), 46)
    s.chord(harp, s.at(35), 3.8, [53, 60, 65, 69, 72], 60, spread=0.1)
    return finish(render(s, room=0.75, damp=0.35, level=0.6))


def olympus():
    """The mountain of the gods, in three: strings and horns in a stately waltz, the harp turning, flute and oboe."""
    rng = random.Random(121)
    prog = ["G", "A", "Em", "G", "C", "D", "Em", "D", "G", "Bm", "C", "A", "Em", "C", "D", "G"] * 3
    h = Harmony(7, LYDIAN, prog, 3)
    s = Score(96, 3, 48)
    strings, horns, harp, flute, oboe, pizz, glock, timp = (s.instrument(STRINGS, 84, 64, 65), s.instrument(HORN, 86, 50, 65), s.instrument(HARP, 90, 36, 60),
                                                            s.instrument(FLUTE, 100, 74, 60), s.instrument(OBOE, 92, 58, 60), s.instrument(PIZZ, 88, 84, 55),
                                                            s.instrument(GLOCK, 66, 96, 70), s.instrument(TIMPANI, 90, 64, 60))
    for b in range(0, 46):
        chord = h.at(b, 0)
        root = 43 + (chord.root - 43) % 12
        s.note(pizz, s.at(b, 0), 0.6, root, 86)
        v = voice(chord, None, 59, 71, 3)
        for beat in (1, 2): s.chord(pizz, s.at(b, beat), 0.4, v[1:], 62)
    arpeggio(s, harp, h, 46, 55, 86, 0.5, [0, 1, 2, 3, 4, 5], vel=54)
    pad(s, strings, h, 32, 55, 74, vel=60, swell=(45, 110), start_bar=8)
    pad(s, horns, h, 14, 52, 67, vel=62, swell=(40, 115), start_bar=28, size=3)
    line = melody(rng, h, 16, [[2, 1], [1.5, 0.5, 1], [1, 1, 1], [3]], 71, 88, (0,), start_bar=8, cadence=[3])
    play(s, flute, line, 92)
    play(s, oboe, thirds_below(h, line), 74)
    play(s, flute, melody(random.Random(122), h, 16, [[1, 2], [2, 1], [1.5, 0.5, 1], [3]], 71, 90, (0,), start_bar=28, cadence=[3]), 96)
    for b in range(8, 46, 4): s.note(glock, s.at(b, 0), 1.0, 79 + h.at(b, 0).root % 12, 60)
    for b in (7, 27, 45):
        for i in range(9): s.note(timp, s.at(b, i / 3), 0.1, drum_pitch(h.at(b, 0), 38), 50 + i * 6)
    s.chord(strings, s.at(46), 5.5, [55, 62, 67, 71, 74], 80)
    s.chord(horns, s.at(46), 5.5, [43, 50, 55, 59], 76)
    return finish(render(s, room=0.7, damp=0.3, level=0.6))


def olympus_peak():
    """The heights of Zeus's tower, where the storm lives: tremolo strings, the brass rising, a choir, thunder on the timpani."""
    rng = random.Random(131)
    prog = ["Em", "C", "G", "D", "Em", "C", "Am", "B7"] * 6
    h = Harmony(4, MINOR, prog, 4)
    s = Score(116, 4, 44)
    trem, brass, horn, choir, timp, cellos, dr = (s.instrument(TREMOLO, 86, 64, 60), s.instrument(BRASS, 92, 50, 55), s.instrument(HORN, 92, 78, 60),
                                                  s.instrument(CHOIR, 84, 64, 75), s.instrument(TIMPANI, 100, 64, 55), s.instrument(STRINGS, 92, 64, 55), s.drums(ORCH_KIT, 90, 50))
    pad(s, trem, h, 44, 52, 71, vel=66, swell=(60, 120))
    arpeggio(s, cellos, h, 40, 40, 55, 0.5, [0, 0, 1, 0, 2, 0, 1, 0], vel=70, start_bar=4, dur=0.4)
    bass(s, timp, h, 40, 40, pattern=((0, 0.5, 0), (2.5, 0.5, 0)), vel=96, start_bar=4)
    pad(s, choir, h, 16, 55, 72, vel=70, swell=(50, 120), start_bar=24)
    line = melody(rng, h, 16, [[2, 1, 1], [1.5, 0.5, 2], [3, 1], [1, 1, 2]], 59, 76, (0, 2), start_bar=8)
    play(s, horn, line, 100)
    play(s, brass, melody(random.Random(132), h, 16, [[1.5, 0.5, 2], [2, 2], [1, 1, 2]], 62, 79, (0, 2), start_bar=24), 104)
    for b in range(8, 42, 2): s.note(dr, s.at(b, 0), 2.0, CRASH, 70 if b % 8 else 100)
    for b in (7, 15, 23, 31, 39):
        for i in range(12): s.note(timp, s.at(b, 2 + i / 6), 0.1, drum_pitch(h.at(b, 2), 38), 50 + i * 6)
    return finish(render(s, room=0.6, damp=0.35, level=0.55))


def abyss():
    """Poseidon's halls under the sea, rolling in six: the harp in waves, strings, voices far off, a clarinet's slow song."""
    rng = random.Random(141)
    prog = ["Bm", "G", "D", "A", "Bm", "Em", "G", "F#7"] * 5
    h = Harmony(11, MINOR, prog, 6)
    s = Score(150, 6, 40)                                  # eighths: two dotted beats to a bar
    harp, strings, oohs, clar, cel, cb, sea = (s.instrument(HARP, 92, 40, 80), s.instrument(SLOW_STRINGS, 74, 64, 90), s.instrument(OOHS, 74, 64, 100),
                                               s.instrument(CLARINET, 96, 74, 80), s.instrument(CELESTA, 62, 96, 100), s.instrument(CONTRABASS, 84, 64, 70),
                                               s.instrument(SEASHORE, 40, 64, 100))
    arpeggio(s, harp, h, 40, 47, 78, 1.0, [0, 2, 4, 5, 4, 2], vel=58)
    pad(s, strings, h, 38, 54, 71, vel=54, swell=(35, 95))
    pad(s, oohs, h, 22, 59, 74, vel=56, swell=(30, 100), start_bar=12)
    bass(s, cb, h, 38, 35, pattern=((0, 5.8, 0),), vel=72)
    play(s, clar, melody(rng, h, 16, [[3, 3], [3, 2, 1], [2, 1, 3], [6]], 62, 78, (0, 3), start_bar=8, cadence=[6]), 86)
    play(s, clar, melody(random.Random(142), h, 12, [[3, 3], [4, 2], [2, 1, 3]], 62, 79, (0, 3), start_bar=26, cadence=[6]), 84)
    for b in range(10, 38, 4): s.note(cel, s.at(b, 3), 2.0, 83 + (h.at(b, 3).root - 83) % 12, 50)
    s.note(sea, s.at(0), 40.0, 59, 60)
    return finish(render(s, room=0.92, damp=0.2, level=0.8))


def abyss_trench():
    """The deepest reaches of Poseidon's tower: low strings and basses barely moving, voices, a bowed shimmer, the dark."""
    rng = random.Random(151)
    prog = ["F#m", "D", "Bm", "C#7", "F#m", "A", "D", "C#7"] * 4
    h = Harmony(6, MINOR, prog, 4)
    s = Score(52, 4, 30)
    cellos, cb, oohs, bowed, eh, cel = (s.instrument(SLOW_STRINGS, 86, 64, 90), s.instrument(CONTRABASS, 92, 64, 80), s.instrument(OOHS, 76, 64, 110),
                                        s.instrument(BOWED, 60, 64, 110), s.instrument(ENGLISH_HORN, 92, 64, 90), s.instrument(CELESTA, 52, 90, 110))
    pad(s, cellos, h, 30, 45, 62, vel=60, swell=(40, 110))
    bass(s, cb, h, 30, 30, pattern=((0, 3.9, 0),), vel=80)
    pad(s, oohs, h, 20, 57, 69, vel=50, swell=(25, 95), start_bar=6)
    pad(s, bowed, h, 30, 66, 78, vel=40, swell=(20, 70))
    play(s, eh, melody(rng, h, 12, [[2, 2], [3, 1], [4], [1.5, 0.5, 2]], 57, 72, (0, 2), start_bar=8), 84)
    for b in range(4, 28, 3): s.note(cel, s.at(b, 2), 2.0, 78 + (h.at(b, 2).root - 78) % 12, 42)
    return finish(render(s, room=0.98, damp=0.4, level=0.85))


def underworld():
    """The halls of Hades: a chorale of trombones and tuba, low strings, a choir, the harp low, a bell tolling, a heartbeat."""
    rng = random.Random(161)
    prog = ["Cm", "Fm", "G7", "Cm", "Ab", "Fm", "G", "Cm", "Eb", "Ab", "Fm", "G7"] * 3
    h = Harmony(0, MINOR, prog, 4)
    s = Score(54, 4, 34)
    bones, tuba, strings, choir, harp, bell, timp, cello = (s.instrument(TROMBONE, 84, 50, 80), s.instrument(TUBA, 86, 70, 70), s.instrument(SLOW_STRINGS, 80, 64, 85),
                                                            s.instrument(CHOIR, 80, 64, 95), s.instrument(HARP, 80, 40, 70), s.instrument(TUBULAR, 74, 90, 90),
                                                            s.instrument(TIMPANI, 92, 64, 70), s.instrument(CELLO, 96, 40, 75))
    pad(s, strings, h, 34, 48, 65, vel=62, swell=(40, 105))
    pad(s, bones, h, 22, 46, 60, vel=58, swell=(40, 100), start_bar=6, size=3)
    bass(s, tuba, h, 30, 29, pattern=((0, 3.8, 0),), vel=70, start_bar=4)
    pad(s, choir, h, 18, 55, 70, vel=60, swell=(30, 105), start_bar=14)
    arpeggio(s, harp, h, 34, 36, 60, 1.0, [0, 1, 2, 1], vel=50)
    play(s, cello, melody(rng, h, 12, [[2, 2], [1.5, 0.5, 2], [3, 1], [1, 1, 2]], 50, 65, (0, 2), start_bar=8), 92)
    for b in range(2, 32):
        s.note(timp, s.at(b, 0), 0.8, 36, 84)
        s.note(timp, s.at(b, 0.55), 0.8, 36, 62)
    for b in range(0, 34, 4): s.note(bell, s.at(b, 2), 4.0, 60, 70)
    return finish(render(s, room=0.85, damp=0.55, level=0.75))


def tartarus():
    """Tartarus, the pit beneath the Underworld: low brass and a choir in fear, taiko pounding, strings shuddering."""
    rng = random.Random(171)
    prog = ["C#m", "A", "D", "G#7", "C#m", "F#m", "D", "G#7"] * 6
    h = Harmony(1, MINOR, prog, 4)
    s = Score(84, 4, 44)
    trem, bones, tuba, choir, taiko, timp, dr = (s.instrument(TREMOLO, 84, 64, 70), s.instrument(TROMBONE, 96, 50, 65), s.instrument(TUBA, 96, 70, 60),
                                                 s.instrument(CHOIR, 86, 64, 80), s.instrument(TAIKO, 110, 64, 55), s.instrument(TIMPANI, 100, 64, 60), s.drums(ORCH_KIT, 90, 60))
    pad(s, trem, h, 44, 49, 68, vel=62, swell=(50, 115))
    pad(s, bones, h, 40, 44, 58, vel=76, swell=(60, 120), size=3, start_bar=4)
    bass(s, tuba, h, 40, 25, pattern=((0, 1.8, 0), (2, 1.8, 0)), vel=82, start_bar=4)
    pad(s, choir, h, 24, 56, 73, vel=74, swell=(50, 120), start_bar=16)
    play(s, choir, melody(rng, h, 8, [[2, 2], [3, 1], [4]], 61, 73, (0, 2), start_bar=32), 90)
    for b in range(4, 42):
        for beat, v in ((0, 120), (1.5, 90), (2, 110), (3.5, 80)): s.note(taiko, s.at(b, beat), 0.6, drum_pitch(h.at(b, beat)), v)
        if b % 4 == 3:
            for i in range(8): s.note(timp, s.at(b, 2 + i / 4), 0.1, drum_pitch(h.at(b, 2), 36), 60 + i * 7)
            s.note(dr, s.at(b + 1, 0), 2.0, CRASH, 100)
    return finish(render(s, room=0.7, damp=0.5, level=0.6))


def passage():
    """The puzzle halls: pizzicato strings tiptoeing, a harp, a clarinet and a bassoon conversing, celesta, a clock ticking."""
    rng = random.Random(181)
    prog = ["Dm", "Gm", "C", "F", "Bb", "Gm", "A7", "Dm"] * 6
    h = Harmony(2, MINOR, prog, 4)
    s = Score(100, 4, 44)
    pizz, harp, clar, bsn, cel, strings, dr = (s.instrument(PIZZ, 92, 50, 55), s.instrument(HARP, 78, 80, 60), s.instrument(CLARINET, 96, 44, 60),
                                               s.instrument(BASSOON, 92, 84, 60), s.instrument(CELESTA, 66, 96, 70), s.instrument(SLOW_STRINGS, 58, 64, 75), s.drums(0, 70, 30))
    arpeggio(s, pizz, h, 44, 50, 74, 0.5, [0, 2, 1, 3, 2, 4, 3, 1], vel=64)
    bass(s, pizz, h, 40, 38, pattern=((0, 0.5, 0), (2, 0.5, 7)), vel=80, start_bar=2)
    pad(s, strings, h, 36, 57, 69, vel=44, swell=(30, 80), start_bar=6)
    play(s, clar, melody(rng, h, 12, [[1, 0.5, 0.5, 2], [0.5, 0.5, 1, 2], [1, 1, 1, 1], [2, 1, 1]], 62, 77, (0, 2), start_bar=8), 84)
    play(s, bsn, melody(random.Random(182), h, 12, [[1, 1, 2], [0.5, 0.5, 1, 2], [2, 2]], 45, 60, (0, 2), start_bar=20), 86)
    play(s, clar, melody(random.Random(183), h, 8, [[1, 0.5, 0.5, 2], [2, 1, 1]], 62, 79, (0, 2), start_bar=32), 86)
    arpeggio(s, harp, h, 16, 62, 86, 1.0, [0, 1, 2, 3], vel=46, start_bar=20)
    for b in range(4, 42):
        for i in range(4): s.note(dr, s.at(b, i), 0.05, HI_WOOD if i % 2 == 0 else LO_WOOD, 36)
    for b in range(12, 40, 4): s.note(cel, s.at(b, 2), 1.5, 81 + (h.at(b, 2).root - 81) % 12, 52)
    return finish(render(s, room=0.5, damp=0.45, level=0.5))


def trial():
    """A god's trial, at full force: spiccato strings driving, the brass and horns in a heroic theme, a choir, taiko and
    timpani and snare, cymbals crashing, orchestra hits on the turns - building, breaking down, and rising again."""
    rng = random.Random(191)
    prog = ["Dm", "Bb", "F", "C", "Dm", "Bb", "Gm", "A", "Dm", "F", "C", "Bb", "Gm", "Bb", "A", "A7"]
    h = Harmony(2, MINOR, prog * 5, 4)
    s = Score(148, 4, 68)
    vln, low, brass, horn, bones, choir, taiko, timp, hit, dr = (
        s.instrument(STRINGS, 98, 64, 45), s.instrument(STRINGS, 100, 64, 45), s.instrument(BRASS, 100, 44, 50), s.instrument(HORN, 104, 84, 55),
        s.instrument(TROMBONE, 100, 64, 50), s.instrument(CHOIR, 96, 64, 65), s.instrument(TAIKO, 118, 64, 40), s.instrument(TIMPANI, 108, 64, 50),
        s.instrument(ORCH_HIT, 96, 64, 50), s.drums(ORCH_KIT, 105, 45))
    # (the sections fall on the sixteen-bar progression, so a theme played again lands on the same chords)
    sections = {"intro": (0, 8), "a": (8, 24), "b": (24, 40), "climax": (40, 56), "break": (56, 60), "final": (60, 64), "end": (64, 68)}
    sec = lambda b: next(k for k, (a, z) in sections.items() if a <= b < z)
    for b in range(68):
        name = sec(b)
        chord = h.at(b, 0)
        root = 38 + (chord.root - 38) % 12
        if name == "end" and b > 64: break
        # the low strings: the root in driving eighths, a fifth now and then - the engine under everything
        if name not in ("break",):
            for i in range(8):
                m = root + (7 if i in (3, 6) and chord.has(root + 7) else 0)
                s.note(low, s.at(b, i * 0.5), 0.35, m, 92 if i % 2 == 0 else 74)
        # the violins: a sixteenth-note ostinato on the chord's notes
        if name in ("a", "b", "climax", "final"):
            tones = notes_in(chord.pcs, 62, 81)
            fig = [0, 1, 2, 1, 0, 1, 2, 3, 2, 1, 0, 1, 2, 3, 4, 3]
            for i in range(16): s.note(vln, s.at(b, i / 4), 0.2, tones[min(len(tones) - 1, fig[i])], 84 if i % 4 == 0 else 66)
        # percussion: taiko on every bar once it begins, snare driving, crashes on the phrases
        if name != "intro" or b >= 4:
            for beat, v in ((0, 124), (0.75, 92), (1.5, 104), (2, 120), (3, 100), (3.5, 96)): s.note(taiko, s.at(b, beat), 0.4, drum_pitch(h.at(b, beat)), v if name != "break" else v - 40)
        if name in ("b", "climax", "final"):
            for beat in (1, 3): s.note(dr, s.at(b, beat), 0.15, SNARE, 100)
            for beat in (1.75, 3.75): s.note(dr, s.at(b, beat), 0.1, SNARE, 70)
        if b % 4 == 0 and name not in ("intro", "break"): s.note(dr, s.at(b, 0), 2.0, CRASH, 112)
        if b % 8 == 7 and name != "end":
            for i in range(16): s.note(timp, s.at(b, 2 + i / 8), 0.1, root, 60 + i * 4)
            for i in range(8): s.note(dr, s.at(b, 2 + i / 4), 0.1, SNARE, 70 + i * 6)
        # brass: chords punching on the off-beats, trombones holding the root
        if name in ("a", "b", "climax", "final"):
            v = voice(chord, None, 57, 72, 3)
            for beat in (0, 1.5, 3): s.chord(brass, s.at(b, beat), 0.5 if beat else 0.9, v, 104)
            s.note(bones, s.at(b), 3.8, root + 12, 96)
    # the heroic theme on the horns, answered; the choir over the climax with the theme doubled
    theme = melody(rng, h, 16, [[1.5, 0.5, 2], [1, 1, 2], [3, 1], [0.75, 0.25, 1, 2]], 62, 77, (0, 2), start_bar=24)
    play(s, horn, theme, 112)
    # at the climax the theme again, sixteen bars on - over the same chords - the brass doubling it an octave up
    climax = [(b + 16, beat, dur, m) for b, beat, dur, m in theme] + [(b + 32, beat, dur, m) for b, beat, dur, m in theme if 28 <= b < 32]
    play(s, horn, climax, 120)
    play(s, brass, [(b, beat, dur, m + 12) for b, beat, dur, m in climax], 100)
    pad(s, choir, h, 16, 57, 74, vel=104, swell=None, start_bar=40)
    pad(s, choir, h, 4, 57, 74, vel=108, swell=None, start_bar=60)
    # the break: only the choir and a timpani heartbeat, then the build back
    for b in range(56, 60):
        s.chord(choir, s.at(b), 3.9, voice(h.at(b, 0), None, 55, 72, 4), 80)
        s.note(timp, s.at(b, 0), 0.6, 38, 100)
        s.note(timp, s.at(b, 2), 0.6, 38, 90)
    for b in (8, 24, 40, 60):
        s.chord(hit, s.at(b), 1.0, voice(h.at(b, 0), None, 55, 72, 3), 116)
    s.chord(hit, s.at(64), 2.0, [50, 57, 62, 65], 124)
    s.note(dr, s.at(64), 3.0, CRASH, 127)
    s.note(timp, s.at(64), 3.0, 38, 127)
    s.chord(brass, s.at(64), 3.5, [50, 57, 62, 65, 69], 120)
    s.chord(choir, s.at(64), 3.5, [50, 57, 62, 65, 69], 110)
    return finish(render(s, room=0.55, damp=0.35, level=0.5, gain=0.45))


def summit():
    """A tower's king on the throne at the summit: a great choir chanting over the full orchestra, bells, the drums of war."""
    rng = random.Random(201)
    prog = ["Bm", "G", "D", "A", "Bm", "Em", "F#", "F#7", "G", "D", "A", "Bm", "G", "A", "F#", "Bm"]
    h = Harmony(11, MINOR, prog * 4, 4)
    s = Score(132, 4, 64)
    choir, strings, brass, horn, bones, bells, taiko, timp, dr = (
        s.instrument(CHOIR, 104, 64, 70), s.instrument(STRINGS, 96, 64, 55), s.instrument(BRASS, 100, 50, 55), s.instrument(HORN, 100, 80, 60),
        s.instrument(TROMBONE, 100, 64, 55), s.instrument(TUBULAR, 84, 90, 75), s.instrument(TAIKO, 118, 64, 45), s.instrument(TIMPANI, 106, 64, 55),
        s.drums(ORCH_KIT, 100, 50))
    for b in range(64):
        chord = h.at(b, 0)
        root = 35 + (chord.root - 35) % 12
        quiet = b < 8 or 32 <= b < 36
        for i in range(8): s.note(strings, s.at(b, i * 0.5), 0.35, root + 12 + (7 if i % 4 == 3 and chord.has(root + 19) else 0), 90 if i % 2 == 0 else 72)
        if not quiet:
            s.note(bones, s.at(b), 3.8, root + 12, 98)
            for beat, v in ((0, 124), (1, 96), (2, 118), (2.5, 90), (3, 104)): s.note(taiko, s.at(b, beat), 0.4, drum_pitch(chord), v)
            for beat in (1, 3): s.note(dr, s.at(b, beat), 0.15, SNARE, 96)
            v3 = voice(chord, None, 59, 74, 3)
            for beat in (0, 2.5): s.chord(brass, s.at(b, beat), 0.8, v3, 106)
        if b % 4 == 0 and not quiet: s.note(dr, s.at(b, 0), 2.0, CRASH, 116)
        if b % 2 == 0: s.note(bells, s.at(b, 0), 3.0, 71 + (chord.root - 71) % 12, 84)
        if b % 8 == 7:
            for i in range(16): s.note(timp, s.at(b, 2 + i / 8), 0.1, root + 12, 60 + i * 4)
    pad(s, choir, h, 64, 54, 74, vel=92, swell=(70, 127))
    chant = melody(rng, h, 16, [[2, 2], [1, 1, 2], [3, 1], [1.5, 0.5, 2]], 62, 76, (0, 2), start_bar=16)
    play(s, horn, chant, 112)
    play(s, horn, [(b + 32, beat, dur, m) for b, beat, dur, m in chant if b + 32 < 64], 120)
    play(s, choir, [(b + 32, beat, dur, m + 12) for b, beat, dur, m in chant if b + 32 < 64], 110)
    return finish(render(s, room=0.75, damp=0.3, level=0.6, gain=0.42))


def titan():
    """A fallen Titan on its keep: primal and heavy, in half time - taiko and toms, low brass and basses, a choir roaring."""
    rng = random.Random(211)
    prog = ["Cm", "Ab", "Fm", "G", "Cm", "Eb", "Ab", "G7"]
    h = Harmony(0, MINOR, prog * 6, 4)
    s = Score(92, 4, 44)
    taiko, toms, bones, tuba, cb, choir, trem, timp, dr = (
        s.instrument(TAIKO, 120, 64, 45), s.instrument(MELODIC_TOM, 96, 70, 45), s.instrument(TROMBONE, 104, 50, 55), s.instrument(TUBA, 104, 70, 50),
        s.instrument(CONTRABASS, 100, 64, 50), s.instrument(CHOIR, 98, 64, 65), s.instrument(TREMOLO, 84, 64, 60), s.instrument(TIMPANI, 108, 64, 55), s.drums(ORCH_KIT, 100, 50))
    for b in range(44):
        chord = h.at(b, 0)
        root = 36 + (chord.root - 36) % 12
        if b >= 2:
            for beat, v in ((0, 127), (1.5, 100), (2.25, 92), (3, 118)): s.note(taiko, s.at(b, beat), 0.6, drum_pitch(chord), v)
            tom_tones = sorted(notes_in(chord.pcs, 43, 55), reverse=True)
            for i, beat in enumerate((3.25, 3.5, 3.75)): s.note(toms, s.at(b, beat), 0.2, tom_tones[i % len(tom_tones)], 90)
        s.note(cb, s.at(b), 3.8, root - 12, 96)
        s.note(tuba, s.at(b), 3.8, root - 12, 92)
        if b >= 4: s.chord(bones, s.at(b), 3.8, voice(chord, None, 43, 58, 3), 100)
        if b % 4 == 3:
            for i in range(12): s.note(timp, s.at(b, 2 + i / 6), 0.1, root, 60 + i * 5)
            s.note(dr, s.at(b + 1), 2.0, CRASH, 118)
    pad(s, trem, h, 44, 48, 67, vel=70, swell=(60, 120))
    pad(s, choir, h, 32, 55, 72, vel=88, swell=(60, 127), start_bar=12)
    roar = melody(rng, h, 16, [[2, 2], [3, 1], [1, 1, 2], [4]], 55, 70, (0, 2), start_bar=16)
    play(s, bones, roar, 110)
    play(s, choir, [(b, beat, dur, m + 12) for b, beat, dur, m in roar], 100)
    return finish(render(s, room=0.6, damp=0.45, level=0.55, gain=0.45))


def citadel():
    """A half-kin garrison's citadel: a march of horns and trumpets, strings striding, the snare drum and the timpani."""
    rng = random.Random(221)
    prog = ["Bb", "Eb", "F", "Bb", "Gm", "Eb", "F", "F7", "Bb", "Dm", "Eb", "Bb", "Cm", "F", "F7", "Bb"]
    h = Harmony(10, MAJOR, prog * 3, 4)
    s = Score(104, 4, 48)
    tpt, horn, strings, low, tuba, timp, dr = (s.instrument(TRUMPET, 96, 44, 55), s.instrument(HORN, 92, 84, 60), s.instrument(STRINGS, 84, 64, 55),
                                               s.instrument(STRINGS, 90, 64, 50), s.instrument(TUBA, 96, 64, 45), s.instrument(TIMPANI, 96, 64, 55), s.drums(ORCH_KIT, 96, 45))
    for b in range(48):
        chord = h.at(b, 0)
        root = 34 + (chord.root - 34) % 12
        s.note(tuba, s.at(b, 0), 0.9, root, 92)
        s.note(tuba, s.at(b, 2), 0.9, root + (7 if chord.has(root + 7) else 0), 84)
        tones = notes_in(chord.pcs, 46, 62)
        for i in range(4): s.note(low, s.at(b, i), 0.45, tones[[0, 2, 1, 3][i] % len(tones)], 80)
        if b >= 2:
            for beat, v in ((0, 104), (0.75, 70), (1, 88), (2, 100), (2.75, 70), (3, 90), (3.5, 76)): s.note(dr, s.at(b, beat), 0.12, SNARE, v)
            s.note(timp, s.at(b, 0), 0.6, root + 12, 90)
        if b % 4 == 3:
            for i in range(12): s.note(dr, s.at(b, 3 + i / 12), 0.05, SNARE, 60 + i * 4)
            s.note(dr, s.at(b + 1, 0), 1.5, CRASH, 96)
    pad(s, strings, h, 40, 58, 74, vel=58, swell=(45, 105), start_bar=8)
    tune = melody(rng, h, 16, [[1.5, 0.5, 1, 1], [1, 1, 2], [0.75, 0.25, 1, 2], [2, 1, 1]], 65, 80, (0, 2), start_bar=4)
    play(s, tpt, tune, 104)
    play(s, horn, thirds_below(h, tune), 88)
    tune2 = [(b + 16, beat, dur, m) for b, beat, dur, m in tune]
    play(s, tpt, tune2, 110)
    play(s, horn, thirds_below(h, tune2), 94)
    play(s, horn, melody(random.Random(222), h, 4, [[2, 2], [3, 1]], 58, 70, (0, 2), start_bar=40), 96)
    return finish(render(s, room=0.55, damp=0.35, level=0.5))


def gate():
    """The Ruined Gate: the synthesised track, kept exactly as it was."""
    import score
    return score.gate()


TRACKS = {"hub": hub, "hermitage": hermitage, "olympus": olympus, "olympus_peak": olympus_peak, "abyss": abyss, "abyss_trench": abyss_trench,
          "underworld": underworld, "tartarus": tartarus, "passage": passage, "trial": trial, "summit": summit, "titan": titan,
          "citadel": citadel, "gate": gate}
