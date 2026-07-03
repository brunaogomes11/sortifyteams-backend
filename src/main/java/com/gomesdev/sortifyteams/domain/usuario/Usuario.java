package com.gomesdev.sortifyteams.domain.usuario;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.f4b6a3.ulid.UlidCreator;
import com.gomesdev.sortifyteams.domain.auth.request.RegisterRequest;
import com.gomesdev.sortifyteams.enums.RoleEnum;
import com.gomesdev.sortifyteams.enums.StatusUsuarioEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "tb_usuario")
@Schema(description = "Entidade que representa um usuário do sistema (jogador, dono de quadra ou admin)")
public class Usuario implements UserDetails {

    @Id
    @Column(name = "id", updatable = false, nullable = false, length = 26)
    @Schema(description = "Identificador único ULID do usuário")
    private String id;

    @Column(name = "nome_completo", nullable = false)
    @Schema(description = "Nome completo do usuário")
    private String nomeCompleto;

    @Column(name = "username", nullable = false, unique = true, length = 40)
    @Schema(description = "Nome de usuário para login")
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    @Schema(description = "E-mail do usuário")
    private String email;

    @Column(name = "senha")
    @JsonIgnore
    @Schema(description = "Senha criptografada com BCrypt", hidden = true)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Schema(description = "Papel do usuário", allowableValues = {"JOGADOR", "DONO_QUADRA", "ADMIN"})
    private RoleEnum role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Schema(description = "Status de aprovação (relevante para DONO_QUADRA)", allowableValues = {"APROVADO", "PENDENTE", "REJEITADO"})
    private StatusUsuarioEnum status;

    @Column(name = "foto_perfil")
    @Schema(description = "URL da foto de perfil do usuário")
    private String fotoPerfil;

    @Column(name = "contato", length = 40)
    @Schema(description = "Telefone/contato do usuário")
    private String contato;

    @Column(name = "esporte_preferido_id", length = 26)
    @Schema(description = "ID ULID do esporte preferido definido manualmente (override do cálculo por histórico)")
    private String esportePreferidoId;

    @Column(name = "criado_em", nullable = false)
    @Schema(description = "Data/hora de criação da conta")
    private LocalDateTime criadoEm;

    public Usuario() {
    }

    public Usuario(RegisterRequest request, String senhaCriptografada) {
        this.nomeCompleto = request.nomeCompleto();
        this.username = request.username();
        this.email = request.email();
        this.senha = senhaCriptografada;
        this.role = request.role();
        this.status = request.role() == RoleEnum.DONO_QUADRA
                ? StatusUsuarioEnum.PENDENTE
                : StatusUsuarioEnum.APROVADO;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UlidCreator.getUlid().toString();
        }
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }

    // UserDetails contract

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // Getters e Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    public RoleEnum getRole() { return role; }
    public void setRole(RoleEnum role) { this.role = role; }
    public StatusUsuarioEnum getStatus() { return status; }
    public void setStatus(StatusUsuarioEnum status) { this.status = status; }
    public String getFotoPerfil() { return fotoPerfil; }
    public void setFotoPerfil(String fotoPerfil) { this.fotoPerfil = fotoPerfil; }
    public String getContato() { return contato; }
    public void setContato(String contato) { this.contato = contato; }
    public String getEsportePreferidoId() { return esportePreferidoId; }
    public void setEsportePreferidoId(String esportePreferidoId) { this.esportePreferidoId = esportePreferidoId; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    // Equals & HashCode based on id

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return Objects.equals(id, usuario.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
