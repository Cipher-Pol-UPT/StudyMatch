package com.studymatch.enums;

// StudyMatch's own stickers (#214), the mascot in a few moods and seasons, some of them animated. a message only stores the value, the
// frontend draws each one and has its name in en.json and pt.json. a new sticker goes at the end of its pack
public enum Sticker {

  // every day
  HELLO,
  THANK_YOU,
  ON_IT,
  COFFEE_BREAK,

  // exams
  EXAM_PANIC,
  ALL_NIGHTER,
  PASSED,
  GRADUATED,

  // Christmas
  SANTA,
  SNOWMAN,
  GIFT,
  REINDEER,

  // Easter
  BUNNY,
  HATCHING,
  EGG_HUNT,
  CHOCOLATE,

  // Halloween
  GHOST,
  PUMPKIN,
  WITCH,
  VAMPIRE,

  // summer, and the Santos Populares in June
  SUNGLASSES,
  ICE_CREAM,
  POOL_FLOAT,
  SARDINE,

  // reactions, animated in the chat
  LAUGHING,
  MIND_BLOWN,
  PARTY,
  LOVE;

  // the longest name, for the column
  public static final int MAX_NAME_LENGTH = 32;

}
