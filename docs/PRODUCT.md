# Product direction

**Your music, without distractions.**

Taki is a focused Android music player for personal or shared Navidrome and
OpenSubsonic/Subsonic-compatible libraries. It exists to make listening to
music you already own or already have access to feel simple, calm, and fast.

## What Taki is

- A music-only player: browse, search, play, download, and keep listening.
- Simple enough that someone who didn't set up the server can still connect
  and listen with just the address, username, and password they were given.
- Built to keep server and protocol complexity (Subsonic, OpenSubsonic,
  transcoding, certificates, jukebox mode, etc.) out of the everyday
  listening experience. That complexity can exist in advanced settings or
  diagnostics; it should never be required to play a song.

## What Taki is not

- Not a chat client, social network, or sharing platform.
- Not a podcast app.
- Not a server administration tool. Taki connects to a library; it does not
  manage, configure, or scan one.

These are deliberate exclusions, not gaps. Contributions that reintroduce
this kind of scope should be discussed as an explicit change of direction
first, not folded into an unrelated change.

## Server compatibility

- **Navidrome** is the primary compatibility target and the server Taki is
  developed and tested against.
- **OpenSubsonic/Subsonic-compatible** servers are supported on a best-effort
  basis. Behavior can vary between server implementations; when in doubt,
  degrade gracefully rather than assuming a capability exists.

## Working principle: simplify before adding

When evaluating a change, prefer:

- Removing a confusing option over adding a new one.
- A good default over a new setting.
- Reusing an existing pattern (row, menu, error message) over inventing
  another one for a single screen.

A feature earns a place in Taki by making everyday listening easier, not by
matching what another client offers. See [Contributing](CONTRIBUTING.md) for
how this applies to pull requests.
