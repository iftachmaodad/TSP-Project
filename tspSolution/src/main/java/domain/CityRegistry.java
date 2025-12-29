package domain;

import java.util.HashMap;
import java.util.Map;

public final class CityRegistry {
	public enum CalculationStrategy {
		LOCAL_MATH,
		API_REQUIRED
	}
	
	// --- Properties ---
    private static final Map<Class<? extends City>, CalculationStrategy> TYPE_REGISTRY = new HashMap<>();
    static {
        TYPE_REGISTRY.put(AirCity.class, CalculationStrategy.LOCAL_MATH);
        TYPE_REGISTRY.put(GroundCity.class, CalculationStrategy.API_REQUIRED);
    }
    
    // --- Constructor ---
    private CityRegistry() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // --- Methods ---
    public static void register(Class<? extends City> type, CalculationStrategy strategy) {TYPE_REGISTRY.put(type, strategy);}
    public static boolean exists(Class<? extends City> type) {return TYPE_REGISTRY.containsKey(type);}
    
    public static boolean startsWithIgnoreCase(String type) {
    	for(Class<? extends City> cityType : TYPE_REGISTRY.keySet()) {
    		if(type.toLowerCase().startsWith(cityType.getSimpleName().toLowerCase()))
    			return true;
    	}
    	return false;
    }
    
    // --- Getters ---
    public static CalculationStrategy getStrategy(Class<?> cityType) {return TYPE_REGISTRY.get(cityType);}
    public static java.util.Set<Class<? extends City>> getRegisteredTypes() {return java.util.Collections.unmodifiableSet(TYPE_REGISTRY.keySet());}
    
}
