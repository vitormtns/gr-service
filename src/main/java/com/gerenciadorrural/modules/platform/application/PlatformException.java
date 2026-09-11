package com.gerenciadorrural.modules.platform.application;
public class PlatformException extends RuntimeException { private final String code; private final int status; public PlatformException(String code,int status,String message){super(message);this.code=code;this.status=status;} public String code(){return code;} public int status(){return status;} }
