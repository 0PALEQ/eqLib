package com.cookiecraftmods.eqlib.core.clock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClockHandMesh {
    private final List<Vertex> vertices;
    private final double pivotZ;
    private final float uprightRotation;

    public ClockHandMesh(List<Vertex> vertices, double pivotZ, float uprightRotation) {
        this.vertices = Collections.unmodifiableList(new ArrayList<Vertex>(vertices));
        this.pivotZ = pivotZ;
        this.uprightRotation = uprightRotation;
    }

    public List<Vertex> vertices() { return vertices; }
    public double pivotZ() { return pivotZ; }
    public float uprightRotation() { return uprightRotation; }

    public static final class Vertex {
        private final float x, y, z, u, v, nx, ny, nz;

        public Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            this.x = x; this.y = y; this.z = z;
            this.u = u; this.v = v;
            this.nx = nx; this.ny = ny; this.nz = nz;
        }

        public float x() { return x; }
        public float y() { return y; }
        public float z() { return z; }
        public float u() { return u; }
        public float v() { return v; }
        public float nx() { return nx; }
        public float ny() { return ny; }
        public float nz() { return nz; }
    }

    public static ClockHandMesh read(Reader reader, String name) {
        JsonObject model = new JsonParser().parse(reader).getAsJsonObject();
        for (com.google.gson.JsonElement entry : model.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            if (!element.has("name") || !element.get("name").getAsString().equals(name)) continue;
            float[] from = vector(element.getAsJsonArray("from"));
            float[] to = vector(element.getAsJsonArray("to"));
            float[] pivot = vector(element.getAsJsonObject("rotation").getAsJsonArray("origin"));
            List<Vertex> vertices = new ArrayList<>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> faceEntry : element.getAsJsonObject("faces").entrySet()) {
                JsonObject face = faceEntry.getValue().getAsJsonObject();
                float[] uv = vector(face.getAsJsonArray("uv"));
                int turn = face.has("rotation") ? face.get("rotation").getAsInt() / 90 : 0;
                int[][] corners;
                float nx = 0, ny = 0, nz = 0;
                switch (faceEntry.getKey()) {
                    case "north": { corners = new int[][]{{1,1,0},{1,0,0},{0,0,0},{0,1,0}}; nz = -1; break; }
                    case "south": { corners = new int[][]{{0,1,1},{0,0,1},{1,0,1},{1,1,1}}; nz = 1; break; }
                    case "west": { corners = new int[][]{{0,1,0},{0,0,0},{0,0,1},{0,1,1}}; nx = -1; break; }
                    case "east": { corners = new int[][]{{1,1,1},{1,0,1},{1,0,0},{1,1,0}}; nx = 1; break; }
                    case "up": { corners = new int[][]{{0,1,0},{0,1,1},{1,1,1},{1,1,0}}; ny = 1; break; }
                    case "down": { corners = new int[][]{{0,0,1},{0,0,0},{1,0,0},{1,0,1}}; ny = -1; break; }
                    default: throw new IllegalArgumentException("Unknown clock face " + faceEntry.getKey());
                }
                for (int i = 0; i < 4; i++) {
                    int t = (i + turn) % 4;
                    int[] c = corners[i];
                    vertices.add(new Vertex(
                            ((c[0] == 0 ? from[0] : to[0]) - pivot[0]) / 16,
                            ((c[1] == 0 ? from[1] : to[1]) - pivot[1]) / 16,
                            ((c[2] == 0 ? from[2] : to[2]) - pivot[2]) / 16,
                            uv[t < 2 ? 0 : 2] / 16, uv[t == 0 || t == 3 ? 1 : 3] / 16,
                            nx, ny, nz));
                }
            }
            float dx = (from[0] + to[0]) / 2 - pivot[0];
            float dy = (from[1] + to[1]) / 2 - pivot[1];
            return new ClockHandMesh(vertices, pivot[2] / 16.0,
                    (float) Math.toDegrees(Math.atan2(dx, dy)));
        }
        throw new IllegalArgumentException("Missing clock hand " + name);
    }

    private static float[] vector(JsonArray array) {
        float[] result = new float[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsFloat();
        return result;
    }
}
