# gccjs

A simple build tool that uses Google Closure Compiler's Java API to watch files in IDE mode for faster type checking. It also integrates error reporting into OS X Lion's notification system.

### Options:

    --check (-c)          Run checks (some checks only work with --optimize)
    --help (-h)           Displays this message
    --no-warnings (-e)    Treat warnings as errors
    --optimize (-o)       Optimize and minify (default just links to sources)
    --project (-p) FILE   The project file (defaults to project.json)
    --watch (-w)          Builds every time a file changes, implies --check

### Project format (JSON)

    {
      // Required
      "target": "compiled.js",
      "sources": ["foo.js", "bar.js"],

      // Optional
      "externs": ["jquery.externs.js"],
      "before": ["before.sh"],
      "after": ["after.sh"],
      "defines": { "LOGGING": true },
      "wrapper": "(function() {%output%})();"
    }

### Example usage

    $ sudo npm install -g gccjs
    $ echo '/** @type {number} */ var foo = false;' > foo.js
    $ echo '{ "target": "out.js", "sources": ["foo.js"] }' > project.json
    $ gccjs --watch
    Warning: initializing variable (line 1 of foo.js)
    found   : boolean
    required: number
    Success (4.5 seconds)
