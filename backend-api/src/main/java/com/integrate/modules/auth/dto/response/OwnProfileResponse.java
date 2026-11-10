package com.integrate.modules.auth.dto.response;
public record OwnProfileResponse(String fullName, String phone) {
    @Override public String toString() { return "OwnProfileResponse[redacted]"; }
}
