package com.gomesdev.sortifyteams.domain.usuario;

import com.gomesdev.sortifyteams.config.storage.StorageService;
import com.gomesdev.sortifyteams.config.storage.UploadImagem;
import com.gomesdev.sortifyteams.domain.esporte.Esporte;
import com.gomesdev.sortifyteams.domain.esporte.EsporteRepository;
import com.gomesdev.sortifyteams.domain.racha.RachaRepository;
import com.gomesdev.sortifyteams.domain.usuario.request.PerfilRequest;
import com.gomesdev.sortifyteams.domain.usuario.response.PerfilResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final RachaRepository rachaRepository;
    private final EsporteRepository esporteRepository;
    private final StorageService storageService;

    public PerfilService(UsuarioRepository usuarioRepository,
                         RachaRepository rachaRepository,
                         EsporteRepository esporteRepository,
                         StorageService storageService) {
        this.usuarioRepository = usuarioRepository;
        this.rachaRepository = rachaRepository;
        this.esporteRepository = esporteRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public PerfilResponse montar(Usuario principal) {
        Usuario usuario = buscar(principal.getId());
        long rachas = rachaRepository.countConcluidosDoUsuario(usuario.getId());

        // C11: override manual quando definido; senão a moda do histórico.
        boolean manual = usuario.getEsportePreferidoId() != null;
        String esporteId = usuario.getEsportePreferidoId();
        if (esporteId == null) {
            List<String> historico = rachaRepository.esportesMaisJogados(usuario.getId());
            esporteId = historico.isEmpty() ? null : historico.get(0);
        }
        String esporteNome = esporteId == null ? null
                : esporteRepository.findById(esporteId).map(Esporte::getNome).orElse(null);

        String fotoUrl = usuario.getFotoPerfil() == null ? null : storageService.getUrl(usuario.getFotoPerfil());
        return new PerfilResponse(usuario.getId(), usuario.getNomeCompleto(), usuario.getUsername(),
                usuario.getEmail(), usuario.getContato(), fotoUrl, rachas,
                esporteId, esporteNome, manual);
    }

    @Transactional
    public PerfilResponse atualizar(PerfilRequest request, Usuario principal) {
        Usuario usuario = buscar(principal.getId());
        usuario.setNomeCompleto(request.nomeCompleto());
        usuario.setContato(request.contato());
        if (request.esportePreferidoId() != null && !request.esportePreferidoId().isBlank()) {
            esporteRepository.findById(request.esportePreferidoId())
                    .orElseThrow(() -> new EntityNotFoundException("Esporte não encontrado."));
            usuario.setEsportePreferidoId(request.esportePreferidoId());
        } else {
            usuario.setEsportePreferidoId(null); // volta ao cálculo pelo histórico
        }
        usuarioRepository.save(usuario);
        return montar(usuario);
    }

    @Transactional
    public PerfilResponse atualizarFoto(MultipartFile arquivo, Usuario principal) {
        Usuario usuario = buscar(principal.getId());
        UploadImagem.validar(arquivo);
        try {
            String anterior = usuario.getFotoPerfil();
            String key = storageService.store(arquivo, "perfis/" + usuario.getId());
            usuario.setFotoPerfil(key);
            usuarioRepository.save(usuario);
            if (anterior != null) {
                storageService.delete(anterior);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return montar(usuario);
    }

    private Usuario buscar(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado."));
    }
}
