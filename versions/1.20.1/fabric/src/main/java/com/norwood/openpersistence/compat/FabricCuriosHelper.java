package com.norwood.openpersistence.compat;

import com.norwood.openpersistence.platform.services.CuriosHelper;

/**
 * Fabric has no Curios (the equivalent would be Trinkets/Accessories). All methods fall back to the
 * interface's no-op defaults, so the extra-slot feature simply degrades to nothing on Fabric.
 */
public class FabricCuriosHelper implements CuriosHelper {
}
