// ===========================================================================
// Title         : DifGui
// Description   : Graphical interface for Diffusr environment
// Copyright (c) : David Granstrom 2013 
// ===========================================================================

DifGui : DifEngine {

    var model;
    var playBtn, seekFwdBtn, seekBckBtn;
    var playlist, openBtn, mVolKnob;
    var sfView, sf;

    *new {|aModel|
        ^super.new.initDifGui(aModel);
    }

    initDifGui {|m|
        model  = m;
        sfView = SoundFileView()
        // .elasticMode_(true)
        .gridOn_(true)
        .timeCursorOn_(true);
        SimpleController(model)
        .put(\isPlaying, {|changer, what, bool|
            playBtn.value = bool;
        })
        .put(\cursorPos, {|changer, what, pos|
            sfView.cursorPos = pos;
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

    draw {
        var w = Window("Diffusr");
        // w.layout_(
        //     HLayout(sfView)
        // );
        w.setTopLeftBounds(
            Rect(
                (Window.availableBounds.width/2)  - (w.bounds.width/2),
                (Window.availableBounds.height/2) - (w.bounds.height/2),
                w.bounds.width,
                w.bounds.height
            )
        );
        w.view.palette_(QPalette.dark);
        w.view.fixedSize_(Size(w.bounds.width, w.bounds.height));
        ^w.front;
    }
}
