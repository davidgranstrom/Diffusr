// ===========================================================================
// Title         : DifGui
// Description   : Graphical interface for Diffusr environment
// Copyright (c) : David Granstrom 2013 
// ===========================================================================

DifGui : DifEngine {

    var model;
    var <>onPlay, <>onStop;
    var playBtn, stopBtn, seekFwdBtn, seekBckBtn;
    var timeCounter, frameCounter;
    var playlist, openBtn, mVolKnob;
    var window, sfView, sf, curSR;
    var colors;

    *new {|aModel|
        ^super.new.initDifGui(aModel);
    }

    initDifGui {|m|
        model  = m;
        sfView = SoundFileView()
        // .elasticMode_(true)
        .gridOn_(true)
        .timeCursorOn_(true);
        // color scheme
        colors = (
            windowBg:   Color.grey(0.25, 1),
            buttonBg:   Color.grey,
            sectionBg:  Color.grey(0.35, 1),
            subSectionBg:  Color.grey(0.4, 1),
            border:     Color.grey(0.2, 1),
        );

        curSR = library[model.source][\sampleRate];

        SimpleController(model)
        .put(\isPlaying, {|changer, what, bool|
            playBtn.value = bool;
        })
        .put(\cursorPos, {|changer, what, pos|
            sfView.timeCursorPosition = pos;
            defer { 
                timeCounter.string_((pos/curSR).asTimeString); 
                frameCounter.string_(pos); 
            }
        })
        .put(\hasStopped, {
            sfView.timeCursorPosition = 0;
        });
        this.updateView;
        this.draw;
    }

    updateView {
        var src  = model.source;
        var path = library[src][\path];
        var numFrames = library[src][\numFrames];
        if(sf.notNil and:{sf.isOpen}) { sf.close };
        sf = SoundFile.openRead(path);
        sfView.gridResolution_(sf.duration/10);
        sfView.soundfile = sf;
        sfView.readWithTask(0, numFrames);
    }

    transportView {
        var timeCounterView, frameCounterView;
        var v = View();
        seekBckBtn = Button()
        .states_([ [ "<<", Color.black, colors[\buttonBg]] ])
        .action_({|btn|
            if(model.isPlaying) {
                model.stop;
                model.play;
            } {
                model.position = 0;
            }
        });
        playBtn = Button()
        .states_([ 
            [ "Play",  Color.black, Color.green(1, 0.5) ],
            [ "Pause", Color.black, colors[\buttonBg]] 
        ])
        .action_({|btn| 
            if(model.isPlaying.not) { 
                model.play; 
                onPlay !? { onPlay.value }
            } { 
                model.pause; 
                // stop any running patterns (if any) from onPlay-value
            };
        });
        stopBtn = Button()
        .states_([[ "Stop", Color.white, colors[\buttonBg]]])
        .action_({ 
            model.stop; 
            onStop !? { onStop.value }
        });
        timeCounterView = View().background_(colors[\sectionBg]).layout_(
            HLayout(
                StaticText().string_("Time"),
                timeCounter = StaticText().string_(try { model.position.asTimeString } { 0.asTimeString })
            )
        );
        frameCounterView = View().background_(colors[\sectionBg]).layout_(
            HLayout(
                StaticText().string_("Frames"),
                frameCounter = StaticText().string_(try { model.position } { 0 })
            )
        );
        v.background_(colors[\sectionBg]).layout_(
            VLayout(
                HLayout(seekBckBtn, playBtn, stopBtn),
                View().background_(colors[\subSectionBg]).layout_(
                    HLayout(timeCounterView, frameCounterView)
                )
            );
        );
        ^v;
    }

    metadataView {
        var labels, metadata, src, items;
        var font  = Font.sansSerif(12);
        var vl, v = View();
        src    = model.source;
        labels = [
            "Title", "Duration", "Sample rate", 
            "Sample format", "Channels", "Header" 
        ].collect{|str| StaticText().string_(str).font_(font) };
        metadata = library[src].atAll([
            \duration, \sampleRate, \sampleFormat, 
            \numChannels, \headerFormat
        ]);
        metadata[0] = metadata[0].asTimeString; // duration
        // add title
        metadata = metadata.insert(0, src.asString);
        metadata = metadata.collect {|str| 
            View().background_(colors[\buttonBg]).layout_(
                HLayout(
                    StaticText().string_(str).align_(\left).font_(font)
                )
            ).fixedWidth_(100);
        };
        items = [ labels, metadata ].flopWith {|l, m| HLayout(l, m) };
        vl = VLayout();
        items.do(vl.add(_));
        v.background_(colors[\sectionBg]);
        ^v.layout_(vl.spacing_(1));
    }

    libraryView {
        var l, v = View();
        l = ListView().items_(library.keys.as(Array));
        l.selectionAction_({|view| 
            model.source = view.item; 
            this.refresh; 
        });
        ^l;
    }

    refresh {
        // window.refresh;
        this.metadataView;
        // window.refresh;
        curSR = library[model.source][\sampleRate];
    }

    draw {
        window = Window("Diffusr").background_(colors[\windowBg]);
        window.layout_(
            HLayout(
                VLayout(
                    sfView,
                    this.transportView
                ),
                this.metadataView,
                this.libraryView,
            );
        );
        window.setTopLeftBounds(
            Rect(
                (Window.availableBounds.width/2)  - (window.bounds.width/2),
                (Window.availableBounds.height/2) - (window.bounds.height/2),
                window.bounds.width,
                window.bounds.height
            )
        );
        window.view.palette_(QPalette.dark);
        // w.view.fixedSize_(Size(w.bounds.width, w.bounds.height));
        ^window.front;
    }
}
