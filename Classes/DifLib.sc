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

    init {|path|
        library = library ? (); 
        path !? { this.add(path) };
    }

    *newFrom {|dir|
        PathName.filesDo {|f|
            var result, ext    = f.extension;
            var validExtension = "wav, aiff";
            result = ext.containsi(validExtension);
            if(result) {
                this.init(f.fullPath);
            }
        }
    }

    *load {|path|
        this.init;
        this.loadLibrary(path);
    }

    *save {|path|
        try { library.writeArchive(path) } {
            "Could not write file to %.\nCheck your disk permissions.".format(path).throw;
        };
        "Saved library to disk.".postln;
    }

    loadLibrary {|path|
        var lib;
        try { lib = Object.readArchive(path) } {
            "Could not load library".throw;
        };
        library = lib;
        "Loaded library.".postln;
    }

    *open {
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
            d.add(\sampleRate   -> f.sampleRate);
            d.add(\numChannels  -> f.numChannels);
            d.add(\headerFormat -> f.headerFormat);
        });
    }

    *files {
        library.keysDo(_.postln);
    }

    *metadata {
        library.keysValuesDo {|key, val| 
            "-  name: \"%\"\n".postf(key);
            val.asSortedArray.do {|x|
                "|_ %: %\n".postf(x[0], x[1]);
            }
        }
    }
}
