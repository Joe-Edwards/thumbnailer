# Thumbnailer
Thumbnail images using akka-streams

## PNG

### Command Line

Usage: `--in <input file> [--out <output file>] [--scalewidth <width scaling>] [--scaleheight <height scaling>]`

This will scale the input file by the given ratios using an averaging method.

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
* The deflate/inflate stages (taken from akka-http) seem to get upset at some images - unclear why.
* Each chunk is supposed to have CRC check bytes - currently these are just zero bytes. This means any decoder checking them will complain.
* Likely to be horribly inefficient. It uses a `ByteBuffer` for particularly intensive operations, but the streams have significant overhead.
