# Clue Path Finder
A RuneLite plugin to help find the shortest path to an active clue scroll utilizing the [Shortest Path plugin](https://github.com/Skretzo/shortest-path).

## Features
- Automatically finds the shortest path of the active clue scroll

## Pre-requisites
- Install the [Shortest Path](https://runelite.net/plugin-hub/show/shortest-path) plugin and have it enabled

## How to Use the Plugin
1. Install the plugin from the RuneLite [Plugin Hub](https://runelite.net/plugin-hub/) by searching for "[Clue Path Finder](https://runelite.net/plugin-hub/show/clue-path-finder)"
2. Depending on how you configured your Clue Scroll plugin `Identify` settings, the shortest path will be created when you either read or pickup the clue scroll - whenever the clue scroll plugin determines that the clue scroll is active

_If you face any issues with the path not being created please try to drop your clue and pick it back up to regenerate the path._

## Additional Information
Please note that when using this plugin, should you have an existing path set via [Shortest Path](https://github.com/Skretzo/shortest-path), it will be overridden by the clue scroll path should one be determined.

### [Shortest Path Plugin](https://github.com/Skretzo/shortest-path)
Also, keep in mind again that because this plugin relies on the [Shortest Path plugin](https://github.com/Skretzo/shortest-path) for the pathing, if a path is incorrect, it is most likely due to the Shortest Path and/or an outdated cache/collision map. For further assistance, please refer to the [Shortest Path repo](https://github.com/Skretzo/shortest-path).

## Demo
The following video was taken back before the Clue Scroll plugin was updated to support automatically identifying clue scrolls based on the user's config. That is why in the video you see the shortest path being created for a new clue whenever I read the clue. That is not the case anymore. 
[![Plugin Demo Video](https://img.youtube.com/vi/VV3tDBFWTfc/maxresdefault.jpg)](https://www.youtube.com/watch?v=VV3tDBFWTfc)
