package com.example.musicbackend.service.impl;

import com.example.musicbackend.dto.request.GenreRequest;
import com.example.musicbackend.dto.response.GenreResponse;
import com.example.musicbackend.entity.Genre;
import com.example.musicbackend.exception.GenreAlreadyExistsException;
import com.example.musicbackend.exception.GenreInUseException;
import com.example.musicbackend.exception.GenreNotFoundException;
import com.example.musicbackend.repository.GenreRepository;
import com.example.musicbackend.repository.SongRepository;
import com.example.musicbackend.service.GenreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenreServiceImpl implements GenreService {

    private final GenreRepository genreRepository;
    private final SongRepository songRepository;

    @Override
    public List<GenreResponse> getAll() {
        return genreRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public GenreResponse getById(Long id) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new GenreNotFoundException(id));
        return toResponse(genre);
    }

    @Override
    @Transactional
    public GenreResponse create(GenreRequest request) {
        String trimmedName = request.getName().trim();

        if (genreRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new GenreAlreadyExistsException(trimmedName);
        }

        Genre genre = new Genre();
        genre.setName(trimmedName);

        return toResponse(genreRepository.save(genre));
    }

    @Override
    @Transactional
    public GenreResponse update(Long id, GenreRequest request) {
        Genre genre = genreRepository.findById(id)
                .orElseThrow(() -> new GenreNotFoundException(id));

        String trimmedName = request.getName().trim();

        if (genreRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new GenreAlreadyExistsException(trimmedName);
        }

        genre.setName(trimmedName);

        return toResponse(genre);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!genreRepository.existsById(id)) {
            throw new GenreNotFoundException(id);
        }

        if (songRepository.existsByGenreId(id)) {
            throw new GenreInUseException(id);
        }

        genreRepository.deleteById(id);
    }

    private GenreResponse toResponse(Genre genre) {
        return GenreResponse.builder()
                .id(genre.getId())
                .name(genre.getName())
                .build();
    }
}