package org.diffkit.diff.custom;

import com.google.gson.*;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

public class GsonUtil {

    private static final Gson GSON = new GsonBuilder()
            // skip static fields
            .excludeFieldsWithModifiers(Modifier.STATIC, Modifier.PRIVATE)
            // skip fields annotated with @SkipJson
//            .registerTypeAdapter(Date.class, new DateTypeAdapter())
            .registerTypeAdapter(Boolean.class, new BooleanTypeAdapter())
            .registerTypeAdapter(boolean.class, new BooleanTypeAdapter()).create();

    //***********************Serialization****************************

    /**
     * Util method for {@link Gson#toJson(Object, Type)}}
     */
    public static <T> String toJson(T obj) {
        try {
            return GSON.toJson(obj);
        } catch (Exception e) {
            throw new RuntimeException("Resolve Json Failed", e);
        }
    }

    public static <T> JsonElement toJsonTree(T obj) {
        try {
            return GSON.toJsonTree(obj);
        } catch (Exception e) {
            throw new RuntimeException("Resolve Json Failed", e);
        }
    }

    /**
     * Util method for {@link Gson#fromJson(String, Type)}
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String string, Type type) {
        try {
            return (T) GSON.fromJson(string, type);
        } catch (Exception e) {
            throw new RuntimeException("Resolve Json Failed", e);
        }
    }

    /**
     * Util method for {@link Gson#fromJson(String, Class)}
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String string, Class<?> clazz) {
        try {
            return (T) GSON.fromJson(string, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Resolve Json Failed", e);
        }
    }

    /**
     * Util method for {@link Gson#fromJson(JsonElement, Type)}}
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(JsonElement json, Type type){
        try {
            return (T) GSON.fromJson(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Resolve Json Failed", e);
        }
    }

    /**
     * Util method for {@link Gson#fromJson(String, Class)}
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromJson(JsonElement json, Class<?> clazz) {
        try {
            return (T) GSON.fromJson(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Resolve Json Failed", e);
        }
    }

    //***********************Custom Date Serializer****************************

    private static final class BooleanTypeAdapter implements JsonSerializer<Boolean>,
                                                             JsonDeserializer<Boolean> {

        @Override
        public JsonElement serialize(Boolean src, Type typeOfSrc,
                JsonSerializationContext context) {
            return new JsonPrimitive(src);
        }

        @Override
        public Boolean deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            if (json.getAsString().equals("true")) return true;
            if (json.getAsString().equals("false")) return false;
            if (json.getAsInt() == 1) return true;
            if (json.getAsInt() == 0) return false;
            return json.getAsBoolean();
        }
    }

//    private static final class DateTypeAdapter implements JsonSerializer<Date>,
//                                                          JsonDeserializer<Date> {
//
//        @Override
//        public JsonElement serialize(Date src, Type typeOfSrc,
//                JsonSerializationContext context) {
//            return new JsonPrimitive(src.getTime()/1000);
//        }
//
//        @Override
//        public Date deserialize(JsonElement json, Type typeOfT,
//                JsonDeserializationContext context) throws JsonParseException {
//            if (!(json instanceof JsonPrimitive)) {
//                throw new JsonParseException(
//                        "The date should be a string value");
//            }
//
//            String str = json.getAsString();
//            if (str == null || str.length() <= 0) return null;
//            Long dateLong = null;
//            try {
//                // in case timestamp is submitted by iOS
//                dateLong = (str.indexOf(".") > 0)
//                        ? Long.parseLong(str.substring(0, str.indexOf(".")))
//                        : json.getAsLong();
//            } catch (NumberFormatException ignored) {}
//
//            if (dateLong != null) return new Date(dateLong * 1000);
//            else return DateUtil.parseDate(str, "yyyy-MM-dd HH:mm:SS");
//        }
//    }

}
