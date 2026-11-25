"""The MIDI machinery (Score, render) the score is written with, and the sound effects. (The tracks below were the
first version played by real instruments; the score now in use is composed in harmony.py.)

The mod's score, written as MIDI and played by real sampled instruments: FluidSynth with the FluidR3 General MIDI
soundfont (by Frank Wen, released under the MIT licence - Debian/Ubuntu package fluid-soundfont-gm). Every track has
its own instruments - no two share a lead - its own key, register, tempo and metre, and its own hall:

  hub         Rhodes piano, nylon guitar, upright bass, brushed jazz kit      D dorian   76  4/4 swung
  hermitage   music box, kalimba, ocarina, warm pad, birdsong                  F major    66  4/4 rubato
  olympus     flute, harp, pizzicato strings, French horns, glockenspiel       G lydian  112  3/4 waltz
  abyss       marimba, vibraphone, fretless bass, voices "oo", the sea         B minor    54  6/8 rolling
  underworld  church organ, cello, contrabass, choir, timpani heartbeat, bells  C# phryg.  46  5/4
  passage     sitar, shakuhachi, dulcimer, darbuka and riq on hand drums       A hijaz   200  7/8 (2+2+3)
  trial       strings in 16ths, brass, trombones, horns, taiko, orchestra hits E minor   138  4/4
  citadel     Highland bagpipes over their drone, fife, trumpets, tuba, drums  Bb mixo.   84  4/4 march
  gate        halo and crystal pads, celesta, koto, choir, reversed cymbals    Ab lydian  40  free

and the sound effects are built from orchestral layers (timpani rolls, orchestra hits, brass, choir, harp glissandi,
tubular bells, reversed cymbals) with synthesised sub-bass blows and risers under them.
"""
import os
import random
import struct
import subprocess
import tempfile
import wave

import numpy as np

SF2 = os.environ.get("ROC_SOUNDFONT", "/usr/share/sounds/sf2/FluidR3_GM.sf2")
SR = 32000
TPQ = 480


# ------------------------------------------------------------------------------------------------ writing MIDI
def _vlq(n):
    out = [n & 0x7F]
    n >>= 7
    while n:
        out.append(0x80 | (n & 0x7F))
        n >>= 7
    return bytes(reversed(out))


class Score:
    """Notes on channels, in beats (MIDI quarter notes at the song's tempo)."""

    def __init__(self, bpm, per_bar, bars, lead=1.0):
        self.bpm, self.per_bar, self.bars, self.lead = bpm, per_bar, bars, lead
        self.events = []            # (tick, order, bytes)
        self.next_channel = 0
        self.rng = random.Random(bpm * 31 + bars)

    def tick(self, beat):
        return max(0, int(round((beat + self.lead) * TPQ)))

    def at(self, bar, beat=0.0):
        return bar * self.per_bar + beat

    def instrument(self, program, volume=100, pan=64, reverb=60, chorus=0, bank=0):
        ch = self.next_channel
        if ch == 9: ch = 10
        self.next_channel = ch + 1
        self._ctl(ch, 0, 0, bank)
        self._ctl(ch, 0, 7, volume)
        self._ctl(ch, 0, 10, pan)
        self._ctl(ch, 0, 91, reverb)
        self._ctl(ch, 0, 93, chorus)
        self._ctl(ch, 0, 11, 127)
        self.events.append((0, 1, bytes([0xC0 | ch, program])))
        return ch

    def drums(self, kit=0, volume=100, reverb=50):
        ch = 9                                  # (FluidSynth keeps channel 10 for drums: the program picks the kit)
        self._ctl(ch, 0, 7, volume)
        self._ctl(ch, 0, 91, reverb)
        self.events.append((0, 1, bytes([0xC9, kit])))
        return ch

    def _ctl(self, ch, beat_tick, ctrl, value):
        self.events.append((beat_tick, 0, bytes([0xB0 | ch, ctrl, max(0, min(127, int(value)))])))

    def cc(self, ch, beat, ctrl, value):
        self._ctl(ch, self.tick(beat), ctrl, value)

    def swell(self, ch, beat, dur, lo, hi, steps=16, ctrl=11):
        """An expression swell: rising to the middle of a span and falling again."""
        for i in range(steps + 1):
            x = i / steps
            v = lo + (hi - lo) * (np.sin(np.pi * x) ** 1.2)
            self.cc(ch, beat + dur * x, ctrl, v)

    def note(self, ch, beat, dur, pitch, vel=90, loose=0.0):
        if pitch < 0 or pitch > 127: return
        b = beat + (self.rng.uniform(-loose, loose) if loose else 0.0)
        v = max(1, min(127, int(vel + (self.rng.uniform(-6, 6) if loose else 0))))
        on, off = self.tick(b), self.tick(b + dur)
        self.events.append((on, 3, bytes([0x90 | ch, pitch, v])))
        self.events.append((max(on + 1, off), 2, bytes([0x80 | ch, pitch, 0])))

    def chord(self, ch, beat, dur, pitches, vel=80, spread=0.0):
        for i, p in enumerate(pitches):
            self.note(ch, beat + i * spread, dur, p, vel)

    def bend(self, ch, beat, value):
        """Pitch bend, -1..1 of the instrument's range (two semitones)."""
        v = int(8192 + max(-1, min(1, value)) * 8191)
        self.events.append((self.tick(beat), 1, bytes([0xE0 | ch, v & 0x7F, v >> 7])))

    def length_beats(self):
        return self.bars * self.per_bar

    def write(self, path):
        evs = sorted(self.events, key=lambda e: (e[0], e[1]))
        data = bytearray()
        tempo = int(60_000_000 / self.bpm)
        data += _vlq(0) + bytes([0xFF, 0x51, 0x03]) + tempo.to_bytes(3, "big")
        last = 0
        for t, _, msg in evs:
            data += _vlq(t - last) + msg
            last = t
        end = self.tick(self.length_beats() + 8)
        data += _vlq(max(0, end - last)) + bytes([0xFF, 0x2F, 0x00])
        with open(path, "wb") as f:
            f.write(b"MThd" + struct.pack(">IHHH", 6, 0, 1, TPQ))
            f.write(b"MTrk" + struct.pack(">I", len(data)) + data)


def render(score, room=0.6, damp=0.4, level=0.7, width=0.8, chorus=False, gain=0.5):
    """Plays a score through FluidSynth into a mono signal at SR."""
    with tempfile.TemporaryDirectory() as tmp:
        mid, wav = os.path.join(tmp, "s.mid"), os.path.join(tmp, "s.wav")
        score.write(mid)
        subprocess.run(["fluidsynth", "-ni", "-q", "-g", str(gain), "-r", str(SR), "-T", "wav", "-F", wav,
                        "-o", f"synth.reverb.room-size={room}", "-o", f"synth.reverb.damp={damp}", "-o", f"synth.reverb.level={level}",
                        "-o", f"synth.reverb.width={width}", "-o", f"synth.chorus.active={1 if chorus else 0}", SF2, mid],
                       check=True, capture_output=True)
        with wave.open(wav) as w:
            n, chans = w.getnframes(), w.getnchannels()
            x = np.frombuffer(w.readframes(n), np.int16).astype(float) / 32768
            sw = w.getsampwidth()
        if chans == 2: x = x.reshape(-1, 2).mean(1)
    # (trim the silence after the last note's tail)
    nz = np.nonzero(np.abs(x) > 1e-4)[0]
    return x[:nz[-1] + int(0.5 * SR)] if len(nz) else x


def finish(x, fade=4.0):
    x = x / max(1e-6, np.max(np.abs(x))) * 0.9
    f = min(len(x) // 3, int(fade * SR))
    x[-f:] *= np.linspace(1, 0, f) ** 1.5
    return x


# ------------------------------------------------------------------------------------------------ music theory
def deg(root, scale, d, octave=0):
    return root + 12 * octave + scale[d % len(scale)] + 12 * (d // len(scale))


def tune(rng, bars, rhythms, lo, hi, start, steps=(-2, -1, -1, 1, 1, 2), leap=0.12):
    out, d = [], start
    for _ in range(bars):
        notes = []
        for dur in rng.choice(rhythms):
            d += rng.choice([-4, -3, 3, 4]) if rng.random() < leap else rng.choice(steps)
            d = max(lo, min(hi, d))
            notes.append((d, dur))
        out.append(notes)
    return out


DORIAN = [0, 2, 3, 5, 7, 9, 10]
MAJOR = [0, 2, 4, 5, 7, 9, 11]
PENT = [0, 2, 4, 7, 9]
LYDIAN = [0, 2, 4, 6, 7, 9, 11]
AEOLIAN = [0, 2, 3, 5, 7, 8, 10]
PHRYGIAN = [0, 1, 3, 5, 7, 8, 10]
HIJAZ = [0, 1, 4, 5, 7, 8, 10]
HARMONIC = [0, 2, 3, 5, 7, 8, 11]
MIXO = [0, 2, 4, 5, 7, 9, 10]

# General MIDI programs (0-based)
RHODES, CELESTA, GLOCK, MUSICBOX, VIBES, MARIMBA, TUBULAR, DULCIMER, ORGAN = 4, 8, 9, 10, 11, 12, 14, 15, 19
NYLON, UPRIGHT, FRETLESS, CELLO, CONTRABASS, TREMOLO, PIZZ, HARP, TIMPANI = 24, 32, 35, 42, 43, 44, 45, 46, 47
STRINGS, SLOW_STRINGS, CHOIR, OOHS, ORCH_HIT, TRUMPET, TROMBONE, TUBA, HORN, BRASS = 48, 49, 52, 53, 55, 56, 57, 58, 60, 61
PICCOLO, FLUTE, SHAKUHACHI, OCARINA, WARM_PAD, HALO, SWEEP, CRYSTAL, ATMOS = 72, 73, 77, 79, 89, 94, 95, 98, 99
SITAR, KOTO, KALIMBA, BAGPIPE, TINKLE, TAIKO, REV_CYMBAL, SEASHORE, BIRDS = 104, 107, 108, 109, 112, 116, 119, 122, 123
# drum notes
KICK, SIDE, SNARE, CLOSED, PEDAL, OPEN, CRASH, RIDE, TAMB, SPLASH = 36, 37, 38, 42, 44, 46, 49, 51, 54, 55
HI_BONGO, LO_BONGO, MUTE_CONGA, HI_CONGA, LO_CONGA, CABASA, MARACAS, CLAVES, HI_WOOD, LO_WOOD, TRIANGLE = 60, 61, 62, 63, 64, 69, 70, 75, 76, 77, 81
BRUSH_KIT, ORCH_KIT, JAZZ_KIT = 40, 48, 32


# ------------------------------------------------------------------------------------------------ the tracks
def hub():
    """The hub: a Rhodes on ninth chords, a nylon guitar picking under it, an upright bass walking, brushes swung."""
    s = Score(76, 4, 36)
    rng = random.Random(11)
    ep, gtr, bass, dr = s.instrument(RHODES, 92, 50, 70, 40), s.instrument(NYLON, 88, 80, 60), s.instrument(UPRIGHT, 100, 64, 40), s.drums(BRUSH_KIT, 85, 40)
    root = 50
    prog = [(0, [0, 3, 7, 10, 14]), (5, [0, 4, 7, 10, 14]), (3, [0, 4, 7, 11, 14]), (-2, [0, 4, 7, 14, 19])]
    swing = lambda i: i * 0.5 + (0.08 if i % 2 else 0.0)            # eighths, swung
    melody = tune(rng, 8, [[1.5, 0.5, 2], [1, 1, 1, 1], [0.5, 1, 0.5, 2], [3, 1], [2, 1.5, 0.5]], 0, 11, 4)
    for b in range(36):
        cr, tones = prog[(b // 2) % 4]
        if b % 2 == 0 and b < 34:
            s.chord(ep, s.at(b), 7.6, [root + cr + x for x in tones[1:]], 58, spread=0.03)
        if 4 <= b < 34:
            # the guitar: a pattern of the chord, swung
            pat = [0, 2, 1, 3, 2, 1, 3, 2]
            for i in range(8):
                s.note(gtr, s.at(b, swing(i)), 0.6, root + 12 + cr + tones[pat[i] % len(tones)], 62 if i % 2 else 72, loose=0.02)
        if 2 <= b < 34:
            # the bass: root, fifth, a walk up to the next
            nxt = prog[((b + 1) // 2) % 4][0]
            walk = [cr, cr + 7, cr + 12 if b % 2 == 0 else cr + 10, (nxt + 11) if b % 2 else cr + 7]
            for i, w in enumerate(walk):
                s.note(bass, s.at(b, i), 0.9, root - 12 + w, 92 - 8 * (i % 2), loose=0.015)
        if 8 <= b < 32:
            for i in range(4):
                s.note(dr, s.at(b, i), 0.3, RIDE, 55 + 10 * (i % 2 == 0))
                s.note(dr, s.at(b, i + 0.58), 0.2, RIDE, 40)
                if i in (1, 3): s.note(dr, s.at(b, i), 0.3, SNARE, 58)
                if i in (0, 2): s.note(dr, s.at(b, i), 0.3, KICK, 60)
        if 12 <= b < 28:
            t = 0.0
            for d, dur in melody[(b - 12) % 8]:
                s.note(ep, s.at(b, t), dur * 0.95, deg(root + 12, DORIAN, d), 80, loose=0.02)
                t += dur
    return finish(render(s, room=0.55, damp=0.5, level=0.55, chorus=True))


def hermitage():
    """Your island: a music box winding through a little tune, a kalimba answering, an ocarina over a warm pad, birds."""
    s = Score(66, 4, 36)
    rng = random.Random(21)
    box, kal, oca, pad, birds = s.instrument(MUSICBOX, 100, 40, 70), s.instrument(KALIMBA, 95, 88, 70), s.instrument(OCARINA, 90, 64, 80), s.instrument(WARM_PAD, 70, 64, 90), s.instrument(BIRDS, 40, 100, 90)
    root = 65
    prog = [(0, [0, 4, 7, 14]), (-3, [0, 3, 7, 10]), (-7, [0, 4, 7, 11]), (-5, [0, 4, 7, 9])]
    motif = [(4, 1), (2, 0.5), (1, 0.5), (0, 1), (2, 1), (3, 1.5), (2, 0.5), (1, 2)]
    song = tune(rng, 4, [[2, 1, 1], [1.5, 0.5, 2], [3, 1], [1, 1, 2]], 0, 8, 4)
    for b in range(36):
        cr, tones = prog[(b // 2) % 4]
        if b % 2 == 0 and b < 34:
            s.chord(pad, s.at(b), 8.0, [root - 12 + cr + x for x in tones], 60)
            s.swell(pad, s.at(b), 8.0, 50, 110)
        if 2 <= b < 32 and b % 2 == 0:
            t = 0.0
            shift = [0, 0, 1, -1, 2, 0, -2, 0][(b // 2) % 8]
            for d, dur in motif:
                s.note(box, s.at(b, t), dur, deg(root + 12, PENT, d + shift), 85, loose=0.03)
                t += dur
        if 6 <= b < 30 and b % 2 == 1:
            for i, d in enumerate(rng.sample(range(6), 4)):
                s.note(kal, s.at(b, 0.75 * i + 1), 1.2, deg(root, PENT, d), 78, loose=0.03)
        if 14 <= b < 30:
            t = 0.0
            for d, dur in song[(b - 14) % 4]:
                s.note(oca, s.at(b, t), dur * 0.97, deg(root + 12, MAJOR, d), 76)
                t += dur
        if b % 6 == 3:
            s.note(birds, s.at(b, rng.uniform(0, 3)), 1.5, 72 + rng.randrange(6), 50)
    return finish(render(s, room=0.75, damp=0.3, level=0.65))


def olympus():
    """A waltz on the mountain: pizzicato oom-pah-pah, the harp rolling, a flute above, horns rising, a glockenspiel's glint."""
    s = Score(112, 3, 72)
    rng = random.Random(31)
    flute, harp, pz, horn, glock, dr = (s.instrument(FLUTE, 105, 70, 60), s.instrument(HARP, 90, 40, 60), s.instrument(PIZZ, 95, 84, 50),
                                        s.instrument(HORN, 80, 50, 70), s.instrument(GLOCK, 70, 96, 70), s.drums(ORCH_KIT, 70, 60))
    root = 55
    prog = [(0, [0, 4, 7, 11]), (2, [0, 4, 7]), (-3, [0, 3, 7, 10]), (-7, [0, 4, 7, 11]), (0, [0, 4, 7, 14]), (2, [0, 4, 7, 10]), (-5, [0, 4, 7, 12]), (2, [0, 5, 7])]
    melody = tune(rng, 8, [[2, 1], [1.5, 0.5, 1], [1, 1, 1], [3], [1, 2], [0.5, 0.5, 2]], 0, 11, 4, steps=(-1, -1, 1, 1, 2, -2, 3))
    melody[7][-1] = (7, melody[7][-1][1])
    for b in range(72):
        cr, tones = prog[(b // 2) % 8]
        sec = 0 if b < 8 else 1 if b < 32 else 2 if b < 56 else 3 if b < 68 else 4
        if sec < 4 or b < 70:
            s.note(pz, s.at(b, 0), 0.5, root - 12 + cr, 96)
            for beat in (1, 2):
                for x in tones[1:3]: s.note(pz, s.at(b, beat), 0.4, root + cr + x, 70)
        if b % 2 == 0:
            for i, x in enumerate(tones + [tones[0] + 12, tones[1] + 12]):
                s.note(harp, s.at(b, i * 0.33), 2.0, root + cr + x, 62)
        if sec in (1, 3) or (sec == 2 and b % 4 < 2):
            t = 0.0
            for d, dur in melody[(b - 8) % 8]:
                s.note(flute, s.at(b, t), dur * 0.95, deg(root + 24, LYDIAN, d), 88)
                t += dur
        if sec == 2 and b % 2 == 0:
            s.chord(horn, s.at(b), 6.0, [root + cr + x for x in tones[:3]], 70)
            s.swell(horn, s.at(b), 6.0, 50, 120)
        if sec >= 1 and b % 4 == 0:
            s.note(glock, s.at(b, 0), 1.0, deg(root + 36, LYDIAN, rng.choice([0, 2, 4, 6])), 70)
            s.note(dr, s.at(b, 0), 1.0, TRIANGLE, 60)
    return finish(render(s, room=0.6, damp=0.25, level=0.55))


def abyss():
    """The deep, in six: a marimba rolling, a vibraphone's slow line, a fretless bass, voices far off, the sea itself."""
    s = Score(162, 6, 48)
    rng = random.Random(41)
    mar, vib, fb, oohs, sea, sweep = (s.instrument(MARIMBA, 95, 50, 80), s.instrument(VIBES, 85, 80, 90), s.instrument(FRETLESS, 100, 64, 60),
                                      s.instrument(OOHS, 78, 64, 110), s.instrument(SEASHORE, 55, 64, 100), s.instrument(SWEEP, 60, 64, 110))
    root = 47
    prog = [(0, [0, 3, 7, 14]), (-4, [0, 4, 7, 11]), (5, [0, 3, 7, 10]), (7, [0, 5, 7, 10])]
    roll = [0, 7, 12, 7, 15, 7]
    for b in range(48):
        cr, tones = prog[(b // 2) % 4]
        if b % 2 == 0 and b < 46:
            s.chord(oohs, s.at(b), 12.0, [root + 12 + cr + x for x in tones[:3]], 70)
            s.swell(oohs, s.at(b), 12.0, 40, 115)
            s.note(fb, s.at(b), 5.5, root - 12 + cr, 90)
            s.note(fb, s.at(b, 6), 5.5, root - 12 + cr + (7 if b % 4 else 12), 80)
        if 2 <= b < 46:
            for i, x in enumerate(roll):
                s.note(mar, s.at(b, i), 1.0, root + 12 + cr + x, 80 if i % 3 == 0 else 62, loose=0.03)
        if 12 <= b < 40 and b % 2 == 0:
            line = [(4, 3), (3, 3), (2, 6)] if b % 4 == 0 else [(2, 3), (4, 3), (5, 4), (4, 2)]
            t = 0.0
            for d, dur in line:
                s.note(vib, s.at(b, t), dur, deg(root + 24, AEOLIAN, d), 80)
                t += dur
        if b in (0, 16, 32):
            s.note(sea, s.at(b), 30.0, 60, 70)
        if b in (8, 24, 40):
            s.note(sweep, s.at(b), 12.0, root + 24 + cr, 60)
    return finish(render(s, room=0.95, damp=0.15, level=0.85, width=1.0))


def underworld():
    """The halls of the dead, in five: an organ, a cello lamenting on the flat second, taiko and timpani, a tolling bell."""
    s = Score(46, 5, 22)
    rng = random.Random(51)
    org, cello, cb, timp, bell, choir = (s.instrument(ORGAN, 85, 64, 90), s.instrument(CELLO, 105, 44, 70), s.instrument(CONTRABASS, 95, 84, 70),
                                         s.instrument(TIMPANI, 100, 70, 60), s.instrument(TUBULAR, 80, 90, 90), s.instrument(CHOIR, 80, 64, 100))
    root = 37
    prog = [(0, [0, 7, 12, 15]), (1, [0, 4, 7, 11]), (5, [0, 3, 7]), (7, [0, 5, 7, 12])]
    for b in range(22):
        cr, tones = prog[(b // 2) % 4]
        if b % 2 == 0 and b < 21:
            s.chord(org, s.at(b), 10.0, [root + 12 + cr + x for x in tones], 70)
            s.note(cb, s.at(b), 10.0, root + cr, 80)
            s.swell(cb, s.at(b), 10.0, 60, 120)
        if 4 <= b < 18 and b % 2 == 0:
            s.chord(choir, s.at(b), 10.0, [root + 12 + cr, root + 19 + cr, root + 24 + cr], 66)
            s.swell(choir, s.at(b), 10.0, 40, 110)
        if 2 <= b < 20:
            # a heart beating slowly under the halls: two soft blows of the timpani, then nothing
            s.note(timp, s.at(b, 0), 1.0, root + 12, 95)
            s.note(timp, s.at(b, 0.6), 1.0, root + 12, 70)
            if b >= 8 and b % 2: s.note(timp, s.at(b, 3), 1.0, root + 7, 60)
        if 6 <= b < 18:
            line = [(1, 2), (0, 1), (-1, 1), (0, 1)] if b % 2 == 0 else [(3, 1.5), (1, 1.5), (0, 2)]
            t = 0.0
            for d, dur in line:
                s.note(cello, s.at(b, t), dur * 0.98, deg(root + 12, PHRYGIAN, d), 92)
                t += dur
        if b % 4 == 0:
            s.note(bell, s.at(b, 2), 4.0, root + 36, 80)
        if b in (0, 8, 16, 20):
            for i in range(12): s.note(timp, s.at(b, i * 0.08), 0.1, root + 12, 40 + i * 6)
            s.note(timp, s.at(b, 1), 2.0, root + 12, 120)
    return finish(render(s, room=0.9, damp=0.6, level=0.8))


def passage():
    """The puzzle halls, in seven: a sitar turning over a figure, a shakuhachi above, a dulcimer, hand drums - darbuka and riq."""
    s = Score(200, 7, 56)
    rng = random.Random(61)
    sitar, flute, dulc, drone, dr = (s.instrument(SITAR, 100, 50, 50), s.instrument(SHAKUHACHI, 95, 80, 70), s.instrument(DULCIMER, 80, 30, 60),
                                     s.instrument(SITAR, 60, 64, 60), s.drums(0, 100, 30))
    root = 57
    figure = [0, 4, 1, 4, 0, 3, 1]
    for b in range(56):
        shift = [0, 0, -2, 1][(b // 4) % 4]
        if b % 4 == 0 and b < 54:
            s.note(drone, s.at(b), 27.0, root - 12, 60)
            s.note(drone, s.at(b), 27.0, root - 5, 50)
        if 2 <= b < 54:
            for i, d in enumerate(figure):
                s.note(sitar, s.at(b, i), 0.9, deg(root, HIJAZ, d + shift), 88 if i in (0, 2, 4) else 70, loose=0.02)
        if 4 <= b < 52:
            # darbuka: doum on the beats of each group (2+2+3), tek between; the riq jingling
            for i in range(7):
                if i in (0, 2, 4): s.note(dr, s.at(b, i), 0.4, LO_CONGA if i == 0 else MUTE_CONGA, 105 if i == 0 else 85)
                elif i in (3, 6) or rng.random() < 0.35: s.note(dr, s.at(b, i), 0.2, HI_BONGO, 75)
                s.note(dr, s.at(b, i + 0.5), 0.1, TAMB, 40 + (15 if i in (0, 4) else 0))
        if 16 <= b < 44 and b % 2 == 0:
            line = tune(rng, 1, [[2, 2, 3], [3, 2, 2], [4, 3], [5, 2]], 0, 9, 4)[0]
            t = 0.0
            for d, dur in line:
                s.note(flute, s.at(b, t), dur * 1.6, deg(root + 12, HIJAZ, d), 90)
                s.bend(flute, s.at(b, t), -0.25)
                s.bend(flute, s.at(b, t + 0.25), 0.0)
                t += dur * 2
        if 28 <= b < 50 and b % 2 == 1:
            for i in range(0, 7, 2):
                s.note(dulc, s.at(b, i), 1.5, deg(root + 12, HIJAZ, rng.choice([0, 2, 4, 5, 7])), 70)
    return finish(render(s, room=0.4, damp=0.5, level=0.45))


def trial():
    """A god's trial: strings driving in sixteenths, the brass section punching, trombones below, taiko, orchestra hits, snare."""
    s = Score(138, 4, 60)
    rng = random.Random(71)
    st, brass, bones, taiko, timp, hit, dr, choir = (s.instrument(STRINGS, 100, 64, 50), s.instrument(BRASS, 100, 50, 50), s.instrument(TROMBONE, 95, 80, 50),
                                                     s.instrument(TAIKO, 115, 64, 40), s.instrument(TIMPANI, 100, 64, 50), s.instrument(ORCH_HIT, 90, 64, 50),
                                                     s.drums(ORCH_KIT, 95, 40), s.instrument(HORN, 100, 64, 60))
    root = 40
    prog = [(0, [0, 3, 7]), (-4, [0, 4, 7]), (-2, [0, 4, 7]), (-5, [0, 4, 7, 10])]
    ost = [0, 0, 7, 0, 0, 12, 7, 0, 0, 0, 7, 0, 3, 0, 5, 7]
    for b in range(60):
        cr, tones = prog[(b // 2) % 4]
        sec = 0 if b < 8 else 1 if b < 24 else 2 if b < 44 else 3 if b < 56 else 4
        if sec < 4 or b < 58:
            for i in range(16):
                x = ost[i] if sec else [0, 0, 7, 0][i % 4]
                s.note(st, s.at(b, i / 4), 0.2, root + 12 + cr + x, 95 if i % 4 == 0 else 72)
        if sec >= 1:
            s.note(taiko, s.at(b, 0), 0.8, 50, 120)
            s.note(taiko, s.at(b, 1.5), 0.5, 50, 90)
            s.note(taiko, s.at(b, 2), 0.8, 53, 105)
        if 2 <= sec < 4:
            for beat in (1, 3): s.note(dr, s.at(b, beat), 0.2, SNARE, 90)
            if b % 8 == 7:
                for i in range(8): s.note(dr, s.at(b, 2 + i / 4), 0.1, SNARE, 60 + i * 7)
                s.note(dr, s.at(b + 1, 0), 2.0, CRASH, 110)
        if sec >= 2 and b % 2 == 0:
            for beat in (0, 1.5, 3):
                s.chord(brass, s.at(b, beat), 0.6, [root + 24 + cr + x for x in tones], 110)
            s.note(bones, s.at(b), 3.5, root + cr, 100)
            s.note(bones, s.at(b, 4), 3.5, root + cr + 7, 95)
        if sec == 3 and b % 2 == 0:
            # the horns ring out over everything at the height of it
            s.chord(choir, s.at(b), 8.0, [root + 12 + cr + x for x in tones], 110)
            s.swell(choir, s.at(b), 8.0, 80, 127)
        if b in (8, 24, 44, 56):
            s.chord(hit, s.at(b), 1.0, [root + 24 + cr + x for x in tones], 110)
        if b in (7, 23, 43):
            for i in range(14): s.note(timp, s.at(b, 2 + i / 7), 0.1, root + 7, 50 + i * 5)
    return finish(render(s, room=0.45, damp=0.4, level=0.4))


def citadel():
    """A garrison's march: Highland bagpipes over their drone, a fife above, trumpet calls, a tuba, snare and bass drum."""
    s = Score(84, 4, 44)
    rng = random.Random(81)
    pipes, drone, fife, tpt, tuba, dr = (s.instrument(BAGPIPE, 100, 64, 50), s.instrument(BAGPIPE, 70, 64, 50), s.instrument(PICCOLO, 85, 90, 50),
                                         s.instrument(TRUMPET, 95, 40, 50), s.instrument(TUBA, 100, 64, 40), s.drums(0, 100, 40))
    root = 58                                               # Bb3
    melody = tune(rng, 8, [[1.5, 0.5, 1, 1], [1, 1, 2], [0.75, 0.25, 1, 2], [2, 1, 1], [1, 0.5, 0.5, 2]], 0, 9, 4, steps=(-1, 1, 1, 2, -2, -1))
    melody[7][-1] = (0, melody[7][-1][1])
    prog = [0, -2, 5, 0]
    for b in range(44):
        cr = prog[(b // 2) % 4]
        if b % 4 == 0 and b < 42:
            s.note(drone, s.at(b), 16.0, root - 12, 80)
            s.note(drone, s.at(b), 16.0, root - 5, 70)
        if 4 <= b < 42:
            t = 0.0
            for d, dur in melody[(b - 4) % 8]:
                s.note(pipes, s.at(b, t), dur * 0.98, deg(root, MIXO, d), 100)
                t += dur
        if b >= 2 and b < 42:
            for i, x in enumerate((0, 7, 0, 7)): s.note(tuba, s.at(b, i), 0.8, root - 24 + cr + x, 95)
            for beat, vel in ((0, 110), (1.75, 70), (2, 100), (2.5, 60), (3, 90), (3.75, 70)): s.note(dr, s.at(b, beat), 0.15, SNARE, vel)
            s.note(dr, s.at(b, 0), 0.3, KICK, 110)
            s.note(dr, s.at(b, 2), 0.3, KICK, 95)
            if b % 4 == 3:
                for i in range(12): s.note(dr, s.at(b, 3 + i / 12), 0.05, SNARE, 50 + i * 4)
        if 20 <= b < 40:
            t = 0.0
            for d, dur in melody[(b + 3) % 8]:
                s.note(fife, s.at(b, t), dur * 0.9, deg(root + 12, MIXO, d + 2), 80)
                t += dur
        if b in (8, 16, 24, 32):
            for i, x in enumerate((0, 4, 7, 12)): s.note(tpt, s.at(b - 1, 2 + i * 0.5), 0.45 if i < 3 else 1.4, root + x, 105)
    return finish(render(s, room=0.5, damp=0.3, level=0.45))


def gate():
    """The dead gate: halo and crystal pads breathing slowly, a celesta and a koto scattered over them, a choir, cymbals reversed."""
    s = Score(40, 4, 20)
    rng = random.Random(91)
    halo, crystal, cel, koto, choir, rev, atmos = (s.instrument(HALO, 90, 64, 110), s.instrument(CRYSTAL, 75, 30, 110), s.instrument(CELESTA, 80, 100, 110),
                                                   s.instrument(KOTO, 85, 40, 90), s.instrument(OOHS, 70, 64, 120), s.instrument(REV_CYMBAL, 70, 64, 90),
                                                   s.instrument(ATMOS, 60, 90, 120))
    root = 56
    prog = [(0, [0, 4, 7, 11, 18]), (2, [0, 4, 7, 14]), (-3, [0, 3, 7, 10, 14]), (-7, [0, 4, 7, 11, 14])]
    for b in range(20):
        cr, tones = prog[(b // 2) % 4]
        if b % 2 == 0 and b < 19:
            s.chord(halo, s.at(b), 8.5, [root + cr + x for x in tones], 70)
            s.swell(halo, s.at(b), 8.5, 30, 115)
            if b >= 4: s.chord(choir, s.at(b), 8.5, [root + cr + x for x in tones[:3]], 60); s.swell(choir, s.at(b), 8.5, 20, 100)
        for i in range(2):
            s.note(cel, s.at(b, rng.uniform(0, 4)), 2.0, deg(root + 24, LYDIAN, rng.choice([0, 1, 2, 4, 5, 6, 7, 9])), 60)
        if b % 2 == 1:
            s.note(koto, s.at(b, rng.uniform(0, 2)), 3.0, deg(root + 12, LYDIAN, rng.choice([0, 2, 4, 7])), 70)
        if b % 4 == 2:
            s.note(crystal, s.at(b), 8.0, deg(root + 24, LYDIAN, rng.choice([0, 4])), 60)
        if b in (3, 11):
            s.note(rev, s.at(b, 1.0), 3.0, 60, 90)
        if b in (0, 10):
            s.note(atmos, s.at(b), 16.0, root + 12, 60)
    return finish(render(s, room=1.0, damp=0.1, level=0.9, width=1.0))


TRACKS = {"hub": hub, "hermitage": hermitage, "olympus": olympus, "abyss": abyss, "underworld": underworld,
          "passage": passage, "trial": trial, "citadel": citadel, "gate": gate}


# ------------------------------------------------------------------------------------------------ effects
def effects():
    """The sound effects: orchestral layers from the soundfont, with synthesised blows and rushes beneath them."""
    import score as synth
    out = {}
    longest = {"gate_awaken": 8.0, "shrine_awaken": 5.0, "keystone_set": 4.5, "orb_make": 3.0, "orb_core": 3.0, "orb_pattern": 2.5, "orb_ichor": 2.5}

    def mix(orch, *layers):
        n = max([len(orch)] + [int((at + len(sig) / SR) * SR) for sig, at, g in layers])
        x = np.zeros(n)
        x[:len(orch)] += orch
        for sig, at, g in layers:
            i = int(at * SR)
            x[i:i + len(sig)] += sig * g
        return finish(x, 0.6)

    # the gate wakes: a reversed cymbal draws the air in; then timpani, taiko, an orchestra hit, the brass section, choir, bells
    s = Score(60, 4, 3, lead=0.0)
    rev, timp, taiko, hit, brass, choir, bell, dr = (s.instrument(REV_CYMBAL, 110), s.instrument(TIMPANI, 110), s.instrument(TAIKO, 120), s.instrument(ORCH_HIT, 110),
                                                     s.instrument(BRASS, 115), s.instrument(CHOIR, 110), s.instrument(TUBULAR, 100), s.drums(ORCH_KIT, 110))
    s.note(rev, 0.0, 2.0, 60, 120)
    for i in range(10): s.note(timp, 1.0 + i * 0.1, 0.1, 44, 50 + i * 7)
    s.note(timp, 2.0, 3.0, 44, 127); s.note(taiko, 2.0, 2.0, 48, 127); s.chord(hit, 2.0, 1.0, [56, 63, 68], 120)
    s.note(dr, 2.0, 3.0, CRASH, 120)
    s.chord(brass, 2.05, 4.5, [56, 63, 68, 72, 75], 120); s.chord(choir, 2.1, 5.0, [56, 60, 63, 68, 72], 115)
    for i, p in enumerate((80, 84, 87, 92)): s.note(bell, 2.5 + i * 0.35, 3.0, p, 90)
    orch = render(s, room=0.9, damp=0.3, level=0.8, gain=0.4)
    out["gate_awaken"] = mix(orch, (synth.boom(4.0, 75, 26), 2.0, 0.5), (synth.riser(2.0, 120, 5000, 13), 0.0, 0.12))
    # a shrine wakes: a harp sweeping up, a choir pad swelling, a bell, a soft timpani
    s = Score(60, 4, 2, lead=0.0)
    harp, cpad, bell, timp = s.instrument(HARP, 110), s.instrument(CHOIR, 105), s.instrument(TUBULAR, 95), s.instrument(TIMPANI, 100)
    for i, p in enumerate([50, 54, 57, 62, 66, 69, 74, 78, 81, 86]): s.note(harp, i * 0.06, 2.5, p, 95)
    s.chord(cpad, 0.3, 3.5, [50, 57, 62, 66, 69], 105); s.swell(cpad, 0.3, 3.5, 40, 127)
    s.note(bell, 0.6, 3.0, 74, 95); s.note(timp, 0.0, 2.0, 38, 100)
    out["shrine_awaken"] = mix(render(s, room=0.85, damp=0.3, level=0.75, gain=0.45), (synth.boom(2.5, 70, 32), 0.0, 0.35))
    # a keystone set: a great bell struck, horns answering, the timpani under it
    s = Score(60, 4, 2, lead=0.0)
    bell, horn, timp, tam = s.instrument(TUBULAR, 120), s.instrument(HORN, 110), s.instrument(TIMPANI, 115), s.drums(ORCH_KIT, 110)
    s.note(bell, 0.0, 3.0, 57, 120); s.note(bell, 0.02, 3.0, 64, 90); s.note(timp, 0.0, 2.0, 33, 127)
    s.chord(horn, 0.25, 2.4, [45, 52, 57, 61], 110); s.swell(horn, 0.25, 2.4, 70, 127)
    s.note(tam, 0.0, 2.0, 52, 90)
    out["keystone_set"] = mix(render(s, room=0.8, damp=0.35, level=0.7, gain=0.45), (synth.boom(2.0, 80, 35), 0.0, 0.4))
    # the orb makes something: a celesta and glockenspiel run, a harp glissando, voices blooming
    s = Score(60, 4, 2, lead=0.0)
    cel, glock, harp, oohs = s.instrument(CELESTA, 110), s.instrument(GLOCK, 90), s.instrument(HARP, 100), s.instrument(OOHS, 100)
    for i, p in enumerate((74, 78, 81, 86, 90, 93)): s.note(cel, i * 0.05, 1.5, p, 95); s.note(glock, i * 0.05 + 0.02, 1.2, p + 12, 70)
    for i, p in enumerate((62, 66, 69, 74, 78, 81, 86)): s.note(harp, 0.1 + i * 0.04, 1.5, p, 90)
    s.chord(oohs, 0.25, 1.8, [74, 78, 81], 100)
    out["orb_make"] = mix(render(s, room=0.7, damp=0.3, level=0.6, gain=0.45), (synth.riser(0.3, 600, 6000, 1), 0.0, 0.1))
    # a core set: a sweep rising, the taiko and a brass swell landing on it
    s = Score(60, 4, 2, lead=0.0)
    sweep, taiko, brass = s.instrument(SWEEP, 110), s.instrument(TAIKO, 120), s.instrument(BRASS, 110)
    s.note(sweep, 0.0, 1.4, 38, 110); s.note(taiko, 1.1, 1.5, 48, 127)
    s.chord(brass, 1.1, 1.2, [50, 57, 62], 115)
    t = np.arange(int(1.1 * SR)) / SR
    hum = sum(np.sin(2 * np.pi * np.cumsum(45 * 6 ** (t / 1.1)) / SR * k) / k for k in range(1, 7)) * (t / 1.1) ** 1.5
    out["orb_core"] = mix(render(s, room=0.6, damp=0.4, level=0.5, gain=0.45), (hum * 0.3, 0.0, 1.0), (synth.boom(1.5, 110, 40), 1.1, 0.5))
    # a pattern shown: a crystal chime and a vibraphone over a short reversed cymbal
    s = Score(60, 4, 2, lead=0.0)
    rev, cry, vib = s.instrument(REV_CYMBAL, 100), s.instrument(CRYSTAL, 110), s.instrument(VIBES, 100)
    s.note(rev, 0.0, 0.7, 72, 100)
    for i, p in enumerate((81, 86, 88, 93)): s.note(cry, 0.6 + i * 0.05, 1.2, p, 95); s.note(vib, 0.62 + i * 0.05, 1.0, p - 12, 80)
    out["orb_pattern"] = mix(render(s, room=0.7, damp=0.3, level=0.6, gain=0.45))
    # ichor poured: a kalimba dropping, voices "oo", bubbles rising through it
    s = Score(60, 4, 2, lead=0.0)
    kal, oohs, fb = s.instrument(KALIMBA, 105), s.instrument(OOHS, 100), s.instrument(FRETLESS, 105)
    for i, p in enumerate((76, 72, 69, 64, 60)): s.note(kal, i * 0.08, 0.8, p, 90)
    s.chord(oohs, 0.25, 1.3, [57, 64], 95); s.note(fb, 0.0, 1.2, 45, 100)
    bubbles = np.zeros(int(1.0 * SR))
    for i in range(8):
        tt = np.arange(int(0.12 * SR)) / SR
        b = np.sin(2 * np.pi * np.cumsum((260 + (i % 3) * 60) * (1 + tt * 9)) / SR) * np.exp(-tt * 30)
        j = int((i * 0.09 + random.Random(i).uniform(0, 0.03)) * SR)
        bubbles[j:j + len(b)] += b[:len(bubbles) - j]
    out["orb_ichor"] = mix(render(s, room=0.6, damp=0.4, level=0.5, gain=0.45), (bubbles, 0.0, 0.25))
    # the screens' click: a woodblock, softly
    s = Score(60, 4, 1, lead=0.0)
    dr = s.drums(0, 100, 0)
    s.note(dr, 0.0, 0.1, HI_WOOD, 80)
    out["ui_click"] = finish(render(s, room=0.1, damp=0.9, level=0.0, gain=0.5)[:int(0.15 * SR)], 0.03)
    # (each cut to a sensible length, its tail faded rather than left ringing into silence)
    for name, secs in longest.items():
        x = out[name][:int(secs * SR)].copy()
        f = int(min(1.5, secs * 0.35) * SR)
        x[-f:] *= np.linspace(1, 0, f) ** 2
        out[name] = x
    return out
