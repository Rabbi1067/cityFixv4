package bd.cityv1.notification;

import bd.cityv1.complaint.Complaint;
import bd.cityv1.complaint.ComplaintRepository;
import bd.cityv1.complaint.Priority;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice(basePackages = "bd.cityv1")
@RequiredArgsConstructor
public class AdminNotificationAdvice {

    private final ComplaintRepository complaintRepository;

    @ModelAttribute
    public void addUrgentComplaints(Model model) {
        List<Complaint> urgentList = complaintRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(c -> c.getPriority() == Priority.HIGH || c.getPriority() == Priority.CRITICAL)
                .limit(10)
                .toList();

        model.addAttribute("urgentNotifications", urgentList);
        model.addAttribute("urgentNotifCount", urgentList.size());
    }
}