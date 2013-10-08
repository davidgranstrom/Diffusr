// =============================================================================
// Title         : DifLib
// Description   : Audio file library
// Copyright (c) : David Granstrom 2013 
// =============================================================================

DifLib {

    classvar <library;

    *new {|path|
        ^super.new.init(path);
    }

    *newFromDir {|dir|
        var d = DifLib.new;
        PathName(dir).filesDo {|f|
            var result, ext    = f.extension;
            var validExtension = "wav, aiff";
            result = validExtension.containsi(ext);
            if(result) {
                d.add(f.fullPath);
            }
        };
        ^d;
    }

    *newFromFile {|file|
        var d = DifLib.new;
        d.load(file);
        ^d;
    }

    init {|path|
        library = library ? (); 
        path !? { this.add(path) };
    }

    load {|path|
        var lib;
        try { lib = Object.readArchive(path) } {
            "Could not load library".throw;
        };
        library = lib;
        "Loaded library.".postln;
    }

    save {|path|
        var stamp = "diflib_" ++ Date.getDate.stamp;
        path = path ?? { Platform.userAppSupportDir +/+ stamp }; 
        try { library.writeArchive(path) } {
            "Could not write file to %.\nCheck your disk permissions.".format(path).throw;
        };
        "Saved library to %\n.".postf(path);
    }

    open {
        Dialog.openPanel({|path|
            path.do(this.add(_));
        }, multipleSelection:true);
    }

    add {|path|
        var key = PathName(path).fileNameWithoutExtension.asSymbol;
        path = path.standardizePath;
        library.put(key, (path: path));
        SoundFile.use(path, {|f|
            var d = library[key];
            d.add(\numFrames    -> f.numFrames);
            d.add(\duration     -> f.duration);
            d.add(\sampleRate   -> f.sampleRate);
            d.add(\numChannels  -> f.numChannels);
            d.add(\headerFormat -> f.headerFormat);
        });
    }

    files {
        if(library.isEmpty.not) {
            library.keysDo(_.postln);
        } {
            "No files in library.".postln;
        }
    }

    metadata {
        if(library.isEmpty.not) {
            library.keysValuesDo {|key, val| 
                "o- NAME: \"%\"\n".postf(key);
                val.asSortedArray.do {|x|
                    if(x[0] == 'duration') {
                        x[1] = x[1].asTimeString;
                    };
                    "|_ %: %\n".postf(x[0], x[1]);
                };
                Post << Char.nl;
            }
        } {
            "No files in library.".postln;
        }
    }

    purge {
        library = ();
    }
}
