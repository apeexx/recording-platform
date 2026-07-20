package com.recording.platform.identity.service;

import org.bson.types.ObjectId;

public final class IdentityIds {
	private IdentityIds() { }

	public static String web() {
		return "WEB-" + new ObjectId().toHexString();
	}

	public static String miniProgram() {
		return "MINI-" + new ObjectId().toHexString();
	}
}
