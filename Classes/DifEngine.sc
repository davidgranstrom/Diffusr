// =============================================================================
// Title         : DifEngine
// Description   : Playback engine and audio processor
// Copyright (c) : David Granstrom 2013
// =============================================================================

DifEngine : DifLib {

    var server, <>source, <ctrlDict;
    var <srcGroup, <diffuserGroup, <mainGroup;
    var <isPlaying;
    // internal
    var buses, bufSize;
    var gBuf, gSyn, gCounter, cursorPos;

    *new {|path, server|
        ^super.new.init(path, server);
    }

    init {|argPath, argServer|
        server        = argServer ? Server.default;
        ctrlDict      = ();
        buses         = List[];
        isPlaying     = false;
        bufSize       = 2**19;
        if(library.isEmpty.not) {
            DifLib().files.do(this.prepare(_));
        } {
            argPath !? { 
                var name = PathName(argPath).fileNameWithoutExtension.asSymbol;
                var d = DifLib(argPath);
                this.prepare(name);
                source = name;
            };
        };
        server.waitForBoot {
            srcGroup      = Group.new(server);
            diffuserGroup = Group.after(srcGroup);
            mainGroup     = Group.after(diffuserGroup);
            server.sync;
            this.makeDefs;
        };
    }

    prepare {|name|
        forkIfNeeded {
            var d = library[name];
            d.put(\srcBus, Bus.audio(server, d[\numChannels]));
            buses.add(d[\srcBus]); // cleanup
            SynthDef(("diffuser_" ++ name).asSymbol, {|out, buf, gate=1, loop=0|
                var env = EnvGen.kr(Env.asr(0.05, 1, 0.05, \sine), gate, doneAction:2);
                var o = VDiskIn.ar(d[\numChannels], buf, BufRateScale.kr(buf), loop);
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

    bus {
        ^library[source][\srcBus];
    }

    numChannels {
        ^library[source][\numChannels];
    }

    play {
        var path, buf, syn, numChannels;
        var key = source ?? { "No source assigned.".throw };
        if(isPlaying.not) {
            path        = library[key][\path];
            numChannels = library[key][\numChannels];
            forkIfNeeded {
                buf = Buffer.cueSoundFile(server, path, cursorPos ? 0, numChannels, bufSize);
                server.sync;
                syn = Synth.head(
                    srcGroup,
                    ("diffuser_" ++ key).asSymbol,
                    [\buf, buf, \out, library[key][\srcBus]]
                ).onFree {
                    buf.close; buf.free;
                    isPlaying = false;
                };
                gCounter = this.counter(
                    library[key][\sampleRate], 
                    library[key][\numFrames],
                    cursorPos ? 0
                ).play(AppClock);
                gSyn  = syn;
            };
            isPlaying = true;
        }
    }

    seek {|time="00:00"|
        var newTime;
        time      = time.split($:).asInteger;
        newTime   = (60*time[0]) + time[1];
        cursorPos = newTime * library[source][\sampleRate];
    }

    pause {
        if(isPlaying) {
            gSyn.release;
            gCounter.stop;
            gSyn      = nil;
            gCounter  = nil;
            isPlaying = false;
        }
    }

    stop {
        if(isPlaying) {
            gSyn.release;
            gCounter.stop;
            cursorPos = nil;
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
