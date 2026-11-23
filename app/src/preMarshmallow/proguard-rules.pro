# @MadeByLLM("gpt-6-astra")
# Room 2.5.0/2.6.1 consumer rules omit the constructor used by reflection.
-keepclassmembers class * extends androidx.room.RoomDatabase {
  public <init>();
}