# JSONTestSuite

The files under `test_parsing/` are copied unchanged from JSONTestSuite by Nicolas Seriot:
https://github.com/nst/JSONTestSuite, commit `1ef36fa01286573e846ac449e8683f8833c5b26a`.
They are distributed under the MIT License, reproduced in `LICENSE` beside this file.

The suite names each file by the verdict a parser should reach: `y_` files must be
accepted, `n_` files must be rejected, and `i_` files may go either way. The test
`klein.host.JsonReaderConformanceTest` walks the directory and holds `JsonReader` to
those verdicts. To refresh the corpus, replace `test_parsing/` and `LICENSE` with the
versions from a newer commit and update the commit hash above.
