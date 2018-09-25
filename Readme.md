# MoodPlayer

> I recommend not cloning the project right now. It is very unpolished and likely to be force-pushed.

This player is written in Kotlin using JavaFX 
and works on both Desktop and Android using JavaFXPorts.
If someone is interested it could also be ported to iOS 
by simply implementing a few platform-specific features.

It is supposed to be complementary to [this absolute monster of a player](https://github.com/sghpjuikit/player)
created by Martin Polakovic, to which I also contribute.
I'll refer to it multiple times in the following paragraphs.

## Concept
I want to integrate things I've always missed in music players 
using some sort of standard into both players. 
Then we have a foundation targeting android and weak devices, 
and Martins full-featured advanced player for use on potent computers. 

These are the particular ideas I would like to look into:
- simple track average volume analysis (ReplayGain or similar)
- beat matching transitions - I am surprised no player I have seen supports this. 
  After downloading [Mixxx](https://github.com/mixxxdj/mixxx) I learned how amazing it sounds, 
  so I want a player that does it automatically.
  1) This will probably first be based on Mixxx, by querying the mixxxdb.sqlite, thus requiring
     you to first scan your Track with Mixxx on the Computer and then sync the db.
  2) After that we might be able to put analysis right into the player using JNI or the like.
  3) A setting will be required where you can set the Beatlength and Cues to use for fading
     or some sort of automatic system
- ‎smart playback - chain songs with similar bpm, or similar according to my ratings system, 
  including smart playlists ("Moods") based on one or more songs
- extendability via plugins

Ideally, both players would provide some sort of common api, so stuff can be plugged into both.
The major difference in that regard would be that Martins Player can live-recompile plugins 
while this player will only accept precompiled plugins to avoid bloating.

### Basic Features that need to be implemented

- tag viewing & editing
- playlists, exporting and importing
- useful queueing (preferrably with multiple queues)

Some platform-specific stuff:
- good configuration for headphone (cable & bluetooth) controls on android
- [MPRIS](https://specifications.freedesktop.org/mpris-spec/latest/interfaces.html) support for linux

### Naming

I've got some naming ideas while brainstorming, but none really clicked yet.
Ideally, the name would also carry over to my ratings system (currently called smartplay)
and Martins player and somewhat convey that one player is basically supposed
to be a mini version of the other.
