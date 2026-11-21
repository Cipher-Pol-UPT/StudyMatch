package com.studymatch.utils;

// create StudyRoom: the Meet link the server makes when the tutor doesn't paste one, tests swap it for a fake
public interface MeetLinkGenerator {

  // throws MEET_LINK_UNAVAILABLE when Google isn't set up, can't be reached or says no
  String createLink();

}
