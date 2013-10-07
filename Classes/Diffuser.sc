// =============================================================================
// Title         : Diffuser
// Description   : Playback engine and audio processing
// Copyright (c) : David Granstrom 2013
// =============================================================================

Diffuser {

    var server, <library, <ctrlDict;
    var <srcGroup, <diffuserGroup, <mainGroup;
    var <isPlaying;
    // internal
    var src, buses, bufSize;
    var gBuf, gSyn, gCounter, cursorPos;

    *new {|path, server|
        ^super.new.init(path, server);
    }

    init {|argSrc, argServer|
        server        = argServer ?? Server.default;
        srcGroup      = Group.new(server);
        diffuserGroup = Group.after(srcGroup);
        mainGroup     = Group.after(diffuserGroup);
        library       = ();
        ctrlDict      = ();
        buses         = List[];
        isPlaying     = false;
        bufSize       = 2**19;
        argSrc !? { this.source = argSrc };
        server.waitForBoot {
            this.makeDefs;
            server.sync;
        };
    }

    open {
        Dialog.openPanel({|paths|
            paths.do(this.prepare(_));
        }, multipleSelection:true);
    }

    source {
        ^src;
    }

    source_ {|path|
        if(path.isString.not) {
            path.do(this.prepare(_));
            src = path.first;
        } {
            this.prepare(path);
            src = path;
        }
    }

    prepare {|src|
        var key = PathName(src).fileNameWithoutExtension.asSymbol;
        src = src.standardizePath;
        library.put(key, (path: src));
        SoundFile.use(src, {|f|
            var d = library[key];
            d.add(\numFrames    -> f.numFrames);
            d.add(\sampleRate   -> f.sampleRate);
            d.add(\numChannels  -> f.numChannels);
            d.add(\headerFormat -> f.headerFormat);
        });
        forkIfNeeded {
            var d = library[key];
            d.put(\srcBus, Bus.audio(server, d[\numChannels]));
            buses.add(d[\srcBus]); // cleanup
            SynthDef(("diffuser_" ++ key).asSymbol, {|out, buf, gate=1, loop=0|
                var env = EnvGen.kr(
                    Env.asr(0.05, 1, 0.05, \sine),
                    gate, doneAction:2
                );
                var o = VDiskIn.ar(
                    d[\numChannels],
                    buf,
                    BufRateScale.kr(buf),
                    loop
                );
                FreeSelfWhenDone.kr(o);
                Out.ar(out, env * o);
            }).add;
        }
    }

    makeDefs {
        // plain vanilla
        SynthDef(\diffuser_plain, {|out, in, amp, gate=1, atk=0.05, rel=0.05|
            var env = EnvGen.kr(
                Env.asr(atk, 1, rel, \sine),
                gate, doneAction:2
            );
            var o = In.ar(in, 1);
            Out.ar(out, amp * o);
        }).add;
        // ---------------------------------------------------------------------
        // processors
        // ---------------------------------------------------------------------
        [
            [
                \diffuser_bpf,
                \diffuser_lpf,
                \diffuser_hpf,
                \diffuser_rm,
                \diffuser_rev,
            ],
            [
                {|x, cfreq=1000, rq=0.8| BPF.ar(x, cfreq, rq) },
                {|x, cfreq=1000| LPF.ar(x, cfreq)             },
                {|x, cfreq=1000| HPF.ar(x, cfreq)             },
                {|x, freq=1000| x * SinOsc.ar(freq)           },
                {|x, revSize=78, revTime=10, drywet=1|
                    var r = GVerb.ar(x * 0.7, revSize, revTime).mean;
                    XFade2.ar(x, r, drywet);
                }
            ]
        ].flopWith {|name, func|
            SynthDef(name, {|in, amp, gate=1, atk=0.05, rel=0.05|
                var xfade = EnvGen.kr(
                    Env.asr(atk, 1, rel, \sine),
                    gate, doneAction:2
                );
                var o = In.ar(in, 1);
                o = SynthDef.wrap(func, nil, o);
                XOut.ar(in, xfade, amp * o);
            }).add;
        };
    }

    makePanner {|name, speakerArray|
        var cbus = Bus.control(server, 1);
        ctrlDict.put(name.asSymbol, cbus);
        SynthDef(name.asSymbol, {|out, in, width=2|
            var o = In.ar(in, 1);
            o = PanAz.ar(speakerArray.size, o, cbus.kr, 1, width, 0);
            Out.ar(speakerArray, o);
        }).add;
    }

    counter {|sampleRate, numFrames, frameOffset=0|
        var updateRate = 1/25;     // 25fps
        numFrames = numFrames + 1; // last sample
        ^Routine {
            inf.do {|i|
                var c = frameOffset + (i*updateRate*sampleRate);
                c = c.asInteger % numFrames;
                this.changed(\cursorPos, cursorPos = c);
                updateRate.wait;
            }
        }
    }

    play {|offset=0|
        var path, buf, syn, numChannels;
        var key = src ?? { "No source file assigned.".throw };
        if(isPlaying.not) {
            key     = PathName(key).fileNameWithoutExtension.asSymbol;
            path    = library[key][\path];
            numChannels = library[key][\numChannels];
            forkIfNeeded {
                buf = Buffer.cueSoundFile(server, path, time, numChannels, bufSize);
                server.sync;
                syn = Synth.head(
                    srcGroup,
                    ("diffuser_" ++ key).asSymbol,
                    [\buf, buf]
                ).onFree {
                    buf.close; buf.free;
                    cursorPos = nil;
                    isPlaying = false;
                };
                gCounter = this.counter(
                    library[key][\sampleRate], 
                    library[key][\numFrames],
                    offset * library[key][\sampleRate]
                ).play(AppClock);
                gSyn  = syn;
                gBuf  = buf;
            };
            isPlaying = true;
        }
    }

    pause {}

    stop {
        if(isPlaying) {
            gSyn.release;
            gCounter.stop;
            gBuf.close;
            gBuf.free;
            gBuf      = nil;
            gSyn      = nil;
            gCounter  = nil;
            isPlaying = false;
        }
    }

    free {
        buses.do(_.free);
        buses = nil;
    }
}
