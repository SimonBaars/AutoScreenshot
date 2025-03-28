#!/bin/bash
adb shell am broadcast -a com.github.cvzi.screenshottile.SCREENSHOT -e secret mypassword com.github.cvzi.screenshottile
