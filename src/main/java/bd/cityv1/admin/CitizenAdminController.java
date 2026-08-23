package bd.cityv1.admin;

import bd.cityv1.citizen.Citizen;
import bd.cityv1.citizen.CitizenAddress;
import bd.cityv1.citizen.CitizenRepository;
import bd.cityv1.complaint.Complaint;
import bd.cityv1.complaint.ComplaintRepository;
import bd.cityv1.complaint.Priority;
import bd.cityv1.complaint.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class CitizenAdminController {

    private final CitizenRepository citizenRepository;
    private final ComplaintRepository complaintRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder; // <-- new: needed to hash the password set on this form

    // Lists all complaints for the admin console.
    @GetMapping("/complaints")
    public String manageComplaints(Model model, Authentication authentication) {

        List<Complaint> complaints = complaintRepository.findAllByOrderByCreatedAtDesc();

        long totalActive = complaints.stream()
                .filter(c -> c.getStatus() != Status.RESOLVED)
                .count();

        long highPriority = complaints.stream()
                .filter(c -> c.getPriority() == Priority.HIGH || c.getPriority() == Priority.CRITICAL)
                .count();
        Admin admin = adminRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        model.addAttribute("activePage", "manage-complaints");
        model.addAttribute("complaints", complaints);
        model.addAttribute("totalActive", totalActive);
        model.addAttribute("highPriority", highPriority);
        model.addAttribute("admin", admin);

        return "admin/complaints";
    }

    @GetMapping("/complaints/{id}/view")
    public String viewComplaint(@PathVariable Long id, Model model) {

        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with id: " + id));

        model.addAttribute("complaint", complaint);

        return "admin/viewComplaint";
    }

    @PostMapping("/complaints/{id}/edit")
    public String updateComplaint(@PathVariable Long id,
                                  @RequestParam Status status,
                                  @RequestParam Priority priority) {

        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with id: " + id));

        complaint.setStatus(status);
        complaint.setPriority(priority);

        complaintRepository.save(complaint);

        return "redirect:/admin/complaints/" + id + "/view";
    }

    @GetMapping("/complaints/{id}/delete")
    public String confirmDeleteComplaint(@PathVariable Long id, Model model) {

        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found with id: " + id));

        model.addAttribute("complaint", complaint);

        return "admin/deleteComplaint";
    }

    @PostMapping("/complaints/{id}/delete")
    public String deleteComplaint(@PathVariable Long id) {

        complaintRepository.deleteById(id);

        return "redirect:/admin/complaints";
    }

    @GetMapping("/users")
    public String manageUsers(Model model, Authentication authentication) {

        List<Citizen> citizens = citizenRepository.findAllByOrderByCreatedAtDesc();

        LocalDateTime startOfMonth = YearMonth.now().atDay(1).atStartOfDay();
        long newThisMonth = citizenRepository.countByCreatedAtAfter(startOfMonth);

        Admin admin = adminRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        model.addAttribute("activePage", "manage-users");
        model.addAttribute("citizens", citizens);
        model.addAttribute("totalRegistered", citizens.size());
        model.addAttribute("newThisMonth", newThisMonth);
        model.addAttribute("admin", admin);

        return "admin/users";
    }

    // Shows the blank "Add User" form (admin-only, keeps the sidebar layout
    // instead of sending the admin to the public /register page)
    @GetMapping("/users/add")
    public String addUserForm(Model model, Authentication authentication) {

        Admin admin = adminRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        model.addAttribute("activePage", "manage-users");
        model.addAttribute("admin", admin);
        model.addAttribute("citizen", new Citizen());

        return "admin/addUser";
    }

    // Saves the citizen created by the admin from the Add User form.
    // citizenId is NOT nullable/auto-generated on the entity, so it must be
    // set here manually. NOTE: I don't have your /register/save controller,
    // so this format ("CTZ-0001", based on the users.html placeholder) is a
    // guess — swap in your real generation logic if it's different, and
    // ideally move this into a shared service so both flows stay in sync.
    @PostMapping("/users/add")
    public String saveUser(@ModelAttribute Citizen citizen, Model model, Authentication authentication) {

        // NOTE: assumes CitizenRepository has existsByEmail(String) and
        // existsByPhone(String) — add them if missing (Spring Data derives
        // the query from the method name, no implementation needed):
        //   boolean existsByEmail(String email);
        //   boolean existsByPhone(String phone);
        boolean emailTaken = citizenRepository.existsByEmail(citizen.getEmail());
        boolean phoneTaken = citizenRepository.existsByPhone(citizen.getPhone());

        if (emailTaken || phoneTaken) {
            Admin admin = adminRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

            model.addAttribute("activePage", "manage-users");
            model.addAttribute("admin", admin);
            model.addAttribute("citizen", citizen);
            if (emailTaken) {
                model.addAttribute("emailError", "This email is already registered.");
            }
            if (phoneTaken) {
                model.addAttribute("phoneError", "This phone number is already registered.");
            }

            return "admin/addUser";
        }

        if (citizen.getAddress() == null) {
            citizen.setAddress(new CitizenAddress());
        }

        citizen.setCitizenId(generateCitizenId());
        citizen.setPassword(passwordEncoder.encode(citizen.getPassword()));
        citizen.setCreatedAt(LocalDateTime.now());

        citizenRepository.save(citizen);

        return "redirect:/admin/users";
    }

    // Simple sequential generator: CTZ-0001, CTZ-0002, ...
    // Not collision-proof if citizens get deleted out of order — replace with
    // whatever your /register/save flow already uses if it exists.
    private String generateCitizenId() {
        long nextNumber = citizenRepository.count() + 1;
        return String.format("CTZ-%04d", nextNumber);
    }

    @GetMapping("/users/{id}/delete")
    public String confirmDelete(@PathVariable Long id, Model model) {

        Citizen citizen = citizenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Citizen not found with id: " + id));

        model.addAttribute("citizen", citizen);

        return "admin/deleteUser";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id) {

        citizenRepository.deleteById(id);

        return "redirect:/admin/users";
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {

        Citizen citizen = citizenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Citizen not found with id: " + id));

        if (citizen.getAddress() == null) {
            citizen.setAddress(new CitizenAddress());
        }

        model.addAttribute("citizen", citizen);

        return "admin/editUser";
    }

    @PostMapping("/users/{id}/edit")
    public String updateUser(@PathVariable Long id, @ModelAttribute Citizen updatedCitizen) {

        Citizen citizen = citizenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Citizen not found with id: " + id));

        citizen.setName(updatedCitizen.getName());
        citizen.setEmail(updatedCitizen.getEmail());
        citizen.setPhone(updatedCitizen.getPhone());

        if (updatedCitizen.getAddress() != null) {
            if (citizen.getAddress() == null) {
                citizen.setAddress(new CitizenAddress());
            }
            citizen.getAddress().setCity(updatedCitizen.getAddress().getCity());
        }

        citizenRepository.save(citizen);

        return "redirect:/admin/users";
    }
}