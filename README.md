# Thumbnailer
Thumbnail images using akka-streams

## PNG

### Command Line

Usage: `--in <input file> [--out <output file>] [--scalewidth <width scaling>] [--scaleheight <height scaling>]`

This will scale the input file by the given ratios using an averaging method.

### Web App

When running go to [http://localhost:8080](http://localhost:8080)

Fill in the form and hit submit, the resized image should appear to the side.

### Library

* Flows to parse a PNG into a stream of chunks and vice-versa
* Flows to decompress/compress the pixel data stream
* Stage to unfilter the pixel data stream
* Stage to filter the pixel data stream using a heuristic to choose an optimal filter for each line.
* Stages to transform unfiltered pixel data by
 * Downsampling (picking a subset of pixels)
 * Averaging (averaging pixels)

### Known Issues

* Does not support interlacing
* The deflate/inflate stages (taken from akka-http) seem to get upset at some images - reasons unclear
  * Reducing the size of the chunks entering the deflater seems to help (indicating there's a bug in the deflater)
  * Some images fail even with very small chunks
  * Seems to happen more with large images, but that may just be because there's more to go wrong
* Likely to be horribly inefficient. It uses a `ByteBuffer` for particularly intensive operations, but the streams have significant overhead.
* The web app behaves quite poorly on failure - as it's all streamed if there's a failure it just times out.
