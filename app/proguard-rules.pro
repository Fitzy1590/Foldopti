# Hilt, Room and WorkManager ship consumer rules. Add app-specific keeps here.
# Shizuku.newProcess is private and called by reflection in ShizukuShell.
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
