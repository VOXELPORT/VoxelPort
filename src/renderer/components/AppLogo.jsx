import React from "react";
import appLogo from "../../../assets/logo.svg";

export default function AppLogo({ className = "", size = 40, alt = "VoxelPort logo" }) {
  return (
    <img
      src={appLogo}
      alt={alt}
      width={size}
      height={size}
      className={className}
      draggable="false"
    />
  );
}
