# SELF IMPROVE

A starter Android app using Kotlin and Jetpack Compose with a black-and-gold interface.

## Included in this starter
- Dashboard with XP total and level progress
- Level progression: level N requires 60 × N XP to advance, with carry-over
- Five prayer checkboxes (Fajr, Dhuhr, Hanafi Asr, Maghrib, Isha)
- Prayer completion: +20 XP; missed confirmation: -20 XP
- Daily habit checkboxes (Exercise, Study, Sleep on time, Daily journal)
- Habit completion: +10 XP; missed confirmation: -10 XP
- XP never falls below zero
- Local persistence for XP, checkboxes and journal
- Daily journal editor

## Not yet implemented
- Solapur date-wise prayer timetable and automatic prayer alarms
- Calendar/history, mood tracker, focus countdown, streaks and achievements
- Daily reset of tracker state
- Production signing and device testing

## Build
GitHub Actions runs on pushes to main and can also be started manually from the Actions tab.
Workflow: Build SELF IMPROVE APK
Artifact: SELF-IMPROVE-debug-APK
