package com.example.musicbackend.controller;

import com.example.musicbackend.dto.request.SongRequest;
import com.example.musicbackend.dto.response.SongResponse;
import com.example.musicbackend.service.SongService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/songs")
@RequiredArgsConstructor
public class AdminSongController {

    private final SongService songService;

    // multipart/form-data: part "metadata" (application/json), "audio" (bắt buộc),
    // "cover" (tùy chọn)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SongResponse> create(
            @Valid @RequestPart("metadata") SongRequest metadata,
            @RequestPart("audio") MultipartFile audio,
            @RequestPart(value = "cover", required = false) MultipartFile cover) {
        return ResponseEntity.status(HttpStatus.CREATED).body(songService.create(metadata, audio, cover));
    }

    @PutMapping("/{id}")
    public SongResponse update(@PathVariable Long id, @Valid @RequestBody SongRequest request) {
        return songService.update(id, request);
    }

    @PutMapping(value = "/{id}/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SongResponse replaceAudio(@PathVariable Long id, @RequestPart("audio") MultipartFile audio) {
        return songService.replaceAudio(id, audio);
    }

    @PutMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SongResponse replaceCover(@PathVariable Long id, @RequestPart("cover") MultipartFile cover) {
        return songService.replaceCover(id, cover);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        songService.delete(id);
        return ResponseEntity.noContent().build();
    }
}