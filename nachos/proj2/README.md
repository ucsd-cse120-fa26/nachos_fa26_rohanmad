# Proj 2

Group Members: Rohan Madan

I used the textbook, slides, and the project spec. I wrote the implementations myself
and tested them with the provided programs, my own C programs, and the shell.


## Methods/Functions I implemented
- **File syscalls (`creat`, `open`, `read`, `write`, `close`, `unlink`)**: each process
  has a 16-entry `fileTable`. Slots 0 and 1 are stdin and stdout. `creat` and `open`
  read the name with `readVirtualMemoryString`, check for a free slot before opening,
  and call `fileSystem.open` (truncate for `creat` only). `close` closes the file and
  nulls the slot so the fd can be reused. `unlink` calls `fileSystem.remove`. All bad
  arguments return -1.
- **read / write**: data goes through a fixed one-page kernel buffer in a loop, so a
  huge user size never allocates a huge array. `read` stops at end of file or on a short
  read (so stdin doesn't block). If a failure happens after some bytes were transferred,
  both return -1 rather than the partial count.
- **Page allocation (`UserKernel`)**: a static free-page `LinkedList` guarded by a Nachos
  `Lock` created after `super.initialize()`. `allocatePages(n)` is all-or-nothing and
  `releasePages` returns pages, so gaps in memory get used.
- **loadSections / unloadSections**: `loadSections` allocates exactly `numPages`, builds
  the `pageTable`, zeroes the pages, loads each COFF section into its real pages, and
  marks read-only sections `readOnly`. `unloadSections` frees the pages and nulls the
  table so a second call does nothing. `unloadSections` also closes the COFF file, which
  fixed a leak where later execs failed with "open failed" even though pages were free.
- **readVirtualMemory / writeVirtualMemory**: both use one `transfer` helper that copies
  one page-sized piece at a time, so buffers can span pages. It checks bounds, `valid`,
  and `readOnly` (writes), and on any problem returns the bytes copied so far. It never
  throws.
- **exec / join**: `exec` reads the filename and `argv` from user memory and returns -1
  on any bad pointer, negative `argc`, missing file, or not enough memory. Each process
  gets a unique pid (root is 0) and the parent keeps a `children` map. `join` only finds
  direct children, so grandchildren, repeat joins, and bad pids return -1. It returns 1
  with the status for a normal exit and 0 for an exception. A bad status pointer returns
  -1 before waiting.
- **exit / halt**: `exit` and the unhandled-exception path share one `cleanup` that
  records the status, closes files, frees memory, and decrements a `numRunning` count.
  The last process to exit calls `Kernel.kernel.terminate()`. `halt` returns -1 unless
  the caller is the root (pid 0).
  

## How I tested them
I wrote C programs in `test/` and also used the provided programs and the shell:

- **File calls**: create, write, close, reopen, read back and compare, then unlink. Also
  bad fds, double close, null and out-of-range buffers, and opening more than 16 files.
- **Memory**: `echo` and `cat` for strings and buffers across pages, and repeated shell
  commands to check that pages are freed on exit.
- **exec / join / exit**: `execjoin` checks join's return value and status, a second
  join, a bad pid, and `exec` with a null name. In the shell, `echo hi there` shows the
  arguments reach the child and the machine only halts when the shell exits.
  - **Extra process tests** (`proctest`): a child that crashes (join returns 0 and the
  status is untouched), a child that calls `halt` (returns -1 and keeps running), a
  grandchild join (-1), 100 sequential exec/join pairs (checks for page leaks), and
  launching several large children at once so some execs fail cleanly for lack of
  memory, then confirming exec works again after they are joined.
- **File tests** (`filetest`): bad fds, null and out-of-range buffers, a 3000-byte
  write and read-back across pages, EOF, unlink, the 16-entry fd limit, and slot reuse.