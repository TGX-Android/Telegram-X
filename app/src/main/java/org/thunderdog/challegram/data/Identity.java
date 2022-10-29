package org.thunderdog.challegram.data;

import org.thunderdog.challegram.loader.ImageFile;

public class Identity {
  private final long id;
  private final Type type;
  private final boolean isLocked;
  private final String name;
  private final String username;
  private final ImageFile avatar;
  private final AvatarPlaceholder.Metadata avatarPlaceholderData;

  public enum Type {
    USER, CHAT, ANONYMOUS
  }

  public Identity(
    long id,
    Type type,
    boolean isLocked,
    String name,
    String username,
    ImageFile avatar,
    AvatarPlaceholder.Metadata avatarPlaceholderData
  ) {
    this.id = id;
    this.type = type;
    this.isLocked = isLocked;
    this.name = name;
    this.username = username;
    this.avatar = avatar;
    this.avatarPlaceholderData = avatarPlaceholderData;
  }

  public long getId () {
    return id;
  }

  public Type getType () {
    return type;
  }

  public boolean isLocked () {
    return isLocked;
  }

  public String getName () {
    return name;
  }

  public String getUsername () {
    return username;
  }

  public ImageFile getAvatar () {
    return avatar;
  }

  public AvatarPlaceholder.Metadata getAvatarPlaceholderData () {
    return avatarPlaceholderData;
  }
}
