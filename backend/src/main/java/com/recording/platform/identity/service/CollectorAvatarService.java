package com.recording.platform.identity.service;

import com.recording.platform.api.ApiException;
import com.recording.platform.config.StoragePathResolver;
import com.recording.platform.identity.model.UserAccount;
import com.recording.platform.identity.model.UserRole;
import com.recording.platform.identity.model.UserStatus;
import com.recording.platform.identity.store.UserStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CollectorAvatarService {
	public static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
	private final UserStore users;
	private final Path root;
	private final Clock clock;

	@Autowired
	public CollectorAvatarService(UserStore users, @Value("${recording.avatar-storage-dir:backend/storage/avatars}") String root, Clock clock) {
		this(users, StoragePathResolver.resolve(root), clock);
	}

	public CollectorAvatarService(UserStore users, Path root, Clock clock) {
		this.users = users; this.root = root.toAbsolutePath().normalize(); this.clock = clock;
	}
	public Path rootPath() { return root; }

	public UserAccount upload(String userId, MultipartFile upload) {
		UserAccount current = requireCollector(userId);
		if (upload == null || upload.isEmpty()) throw invalid("头像文件不能为空");
		if (upload.getSize() > MAX_AVATAR_BYTES) throw tooLarge();
		AvatarType type = detect(upload);
		String relative = safeId(userId) + "." + type.extension;
		Path target = resolve(relative);
		Path temporary = resolve("temp/" + UUID.randomUUID() + "." + type.extension);
		Path backup = null;
		try {
			Files.createDirectories(temporary.getParent());
			try (InputStream input = upload.getInputStream()) { Files.copy(input, temporary); }
			if (Files.size(temporary) > MAX_AVATAR_BYTES) throw tooLarge();
			Files.createDirectories(target.getParent());
			if (Files.exists(target)) {
				backup = resolve("temp/" + UUID.randomUUID() + ".bak");
				atomicMove(target, backup);
			}
			atomicMove(temporary, target);
			UserAccount saved = users.updateCollectorAvatarIfActive(userId, relative, type.contentType, Instant.now(clock))
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"));
			if (current.getAvatarPath() != null && !current.getAvatarPath().equals(relative)) Files.deleteIfExists(resolve(current.getAvatarPath()));
			if (backup != null) Files.deleteIfExists(backup);
			return saved;
		} catch (ApiException exception) {
			restore(target, backup); deleteQuietly(temporary); throw exception;
		} catch (IOException exception) {
			restore(target, backup); deleteQuietly(temporary);
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AVATAR_STORAGE_FAILED", "头像暂时无法保存");
		}
	}

	public AvatarFile read(String userId) {
		UserAccount user = requireCollector(userId);
		if (user.getAvatarPath() == null || user.getAvatarPath().isBlank())
			throw new ApiException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "尚未设置自定义头像");
		Path path = resolve(user.getAvatarPath());
		if (!Files.isRegularFile(path)) throw new ApiException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "头像文件不存在");
		return new AvatarFile(path, user.getAvatarContentType());
	}

	public UserAccount delete(String userId) {
		UserAccount current = requireCollector(userId);
		UserAccount saved = users.clearCollectorAvatarIfActive(userId, Instant.now(clock))
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "ACCOUNT_STATE_CHANGED", "账号状态已变化，请重试"));
		if (current.getAvatarPath() != null) {
			try { Files.deleteIfExists(resolve(current.getAvatarPath())); }
			catch (IOException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AVATAR_DELETE_FAILED", "头像文件暂时无法删除"); }
		}
		return saved;
	}

	private UserAccount requireCollector(String id) {
		UserAccount user = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
		if (user.getRole() != UserRole.COLLECTOR || user.getStatus() != UserStatus.ACTIVE)
			throw new ApiException(HttpStatus.FORBIDDEN, "COLLECTOR_REQUIRED", "仅录音人员可以管理头像");
		return user;
	}

	private AvatarType detect(MultipartFile upload) {
		String name = upload.getOriginalFilename() == null ? "" : upload.getOriginalFilename().toLowerCase(Locale.ROOT);
		try (InputStream input = upload.getInputStream()) {
			byte[] header = input.readNBytes(12);
			if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && starts(header, new int[]{0xff,0xd8,0xff})) return new AvatarType("jpg", "image/jpeg");
			if (name.endsWith(".png") && starts(header, new int[]{0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a})) return new AvatarType("png", "image/png");
			if (name.endsWith(".webp") && header.length >= 12 && new String(header,0,4).equals("RIFF") && new String(header,8,4).equals("WEBP")) return new AvatarType("webp", "image/webp");
		} catch (IOException ignored) { }
		throw invalid("头像仅支持内容有效的 JPEG、PNG 或 WebP 文件");
	}

	private boolean starts(byte[] data, int[] signature) {
		if (data.length < signature.length) return false;
		for (int i=0;i<signature.length;i++) if ((data[i] & 0xff) != signature[i]) return false;
		return true;
	}
	private String safeId(String value) { if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) throw invalid("用户编号不合法"); return value; }
	private Path resolve(String relative) { Path path = root.resolve(relative).normalize(); if (!path.startsWith(root)) throw invalid("头像路径不合法"); return path; }
	private void atomicMove(Path from, Path to) throws IOException { try { Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException e) { throw new IOException("atomic move required",e); } }
	private void restore(Path target, Path backup) { deleteQuietly(target); if (backup != null && Files.exists(backup)) try { atomicMove(backup,target); } catch(IOException ignored){} }
	private void deleteQuietly(Path path) { if(path != null) try { Files.deleteIfExists(path); } catch(IOException ignored){} }
	private ApiException invalid(String message) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_AVATAR_FILE", message); }
	private ApiException tooLarge() { return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AVATAR_TOO_LARGE", "头像文件不能超过 5MB"); }
	private record AvatarType(String extension, String contentType) { }
	public record AvatarFile(Path path, String contentType) { }
}
